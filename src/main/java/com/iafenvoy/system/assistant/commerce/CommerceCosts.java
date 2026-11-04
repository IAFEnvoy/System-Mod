package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.CostPayment;
import com.iafenvoy.saturn.data.cost.CostTarget;
import com.iafenvoy.saturn.data.cost.Debit;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.runtime.resulting.failures.CostFailure;
import com.iafenvoy.saturn.util.DefinitionText;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.saturn.util.matcher.builtin.ItemEntry;
import com.iafenvoy.saturn.util.matcher.builtin.TagEntry;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.store.PurchaseType;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prices Saturn costs once on the game thread; quotes retain that bound bill and expose only display data.
 */
public final class CommerceCosts {
    private CommerceCosts() {
    }

    public static Prepared prepare(List<Cost> costs, ServerPlayer player, PurchaseType type, Identifier source) {
        CostContext context = CostContext.of(player, "system:" + type.getSerializedName()).with("system:source", source);
        CostPayment bill = CostPayment.of(context);
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean uncertain = new AtomicBoolean();
        List<ItemPreviewOutput> items = new ArrayList<>();
        List<Component> descriptions = new ArrayList<>();
        Failure failure = null;
        Identifier failedResource = null;
        for (Cost cost : costs) {
            Result<Cost.Transaction> result = cost.prepare(context);
            if (result instanceof Result.FailureResult<?>) {
                failure = result.getFailure().orElseThrow();
                failedResource = result.getFailure().flatMap(Failure::resource).orElse(null);
                break;
            }
            Cost.Transaction transaction = result.getData().orElseThrow();
            if (!Double.isFinite(transaction.amount()) || transaction.amount() < 0) {
                failure = CostFailure.INVALID_AMOUNT;
                break;
            }
            Optional<Failure> pricingFailure = bill.price(List.of(new BoundCost(cost, new TrackedTransaction(transaction, started, uncertain))));
            if (pricingFailure.isPresent()) {
                failure = pricingFailure.get();
                failedResource = bill.failedResource().orElse(null);
                break;
            }
            if (transaction.amount() == 0) continue;
            if (transaction.target() instanceof CostTarget.Items target && target.entries().size() == 1 && target.entries().getFirst() instanceof ItemEntry(
                    Item item
            ))
                items.add(new ItemPreviewOutput(new ItemStackTemplate(item), number(Math.ceil(transaction.amount()))));
            else if (transaction.target() instanceof CostTarget.Account target)
                descriptions.add(Component.translatableWithFallback(DefinitionText.key(target.resource()), target.resource().unwrapKey().orElseThrow().identifier().toString()).append(" × " + number(transaction.amount())));
            else if (transaction.target() instanceof CostTarget.Items target) {
                MutableComponent alternatives = Component.empty();
                for (ItemMatcher.Entry entry : target.entries()) {
                    if (!alternatives.getSiblings().isEmpty()) alternatives.append(" / ");
                    alternatives.append(describe(entry));
                }
                descriptions.add(Component.translatable("system.cost.any_of", alternatives).append(" × " + number(Math.ceil(transaction.amount()))));
            } else
                descriptions.add(Component.literal(transaction.target().label() + " × " + number(transaction.amount())));
        }
        // The quote always carries a display price, even when pricing was refused, so only the refusal is optional.
        Optional<Failure> refusal = Optional.empty();
        if (failure != null) {
            descriptions.add(failure.message(failedResource));
            refusal = Optional.of(failedResource == null ? failure : failure.withResource(failedResource));
        }
        return new Prepared(player, bill, new CommerceCatalog.Price(items, descriptions), refusal, started, uncertain);
    }

    private static Component describe(ItemMatcher.Entry entry) {
        if (entry instanceof ItemEntry(Item item1))
            return new ItemStackTemplate(item1).create().getHoverName();
        if (entry instanceof TagEntry(
                TagKey<Item> tag1
        )) return Component.literal("#" + tag1.location());
        return Component.translatable("system.cost.custom_item",
                String.valueOf(SaturnRegistries.ITEM_MATCHER_ENTRY_TYPE.getKey(entry.codec())));
    }

    private static String number(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() : Double.toString(value);
    }

    /**
     * The quote owns the immutable display price, which is produced whether or not pricing succeeded; the bound bill
     * stays server-side for confirmation. Only the refusal is optional, so it is carried as a plain failure value
     * rather than as a {@code Result} that never has data.
     */
    public static final class Prepared {
        private final ServerPlayer payer;
        private final CostPayment bill;
        private final CommerceCatalog.Price price;
        private final Optional<Failure> failure;
        private final AtomicBoolean started, uncertain;

        private Prepared(ServerPlayer payer, CostPayment bill, CommerceCatalog.Price price, Optional<Failure> failure,
                         AtomicBoolean started, AtomicBoolean uncertain) {
            this.payer = payer;
            this.bill = bill;
            this.price = price;
            this.failure = failure;
            this.started = started;
            this.uncertain = uncertain;
        }

        public CommerceCatalog.Price getData() {
            return this.price;
        }

        /**
         * {@return why pricing was refused, or empty when the quote is usable}
         */
        public Optional<Failure> failure() {
            return this.failure;
        }

        public boolean isSuccess() {
            return this.failure.isEmpty();
        }

        public boolean isFailure() {
            return this.failure.isPresent();
        }

        public void pay(ServerPlayer player, AtomicBoolean touched) {
            if (player != this.payer) throw new AgentException("STALE_QUOTE");
            if (this.isFailure()) throw new AgentException("INVALID_COST");
            if (this.bill.test().isPresent()) throw new AgentException("INSUFFICIENT_COST");
            Result<List<Debit>> result;
            try {
                result = this.bill.pay();
            } finally {
                touched.set(this.started.get());
            }
            // Saturn continues real fees after an error; never unlock goods for a partial or uncertain collection.
            if (this.uncertain.get()) throw new AgentException("EFFECT_UNKNOWN");
            if (result instanceof Result.FailureResult<?>) {
                throw new AgentException("INSUFFICIENT_COST");
            }
        }
    }

    private record BoundCost(Cost definition, Cost.Transaction transaction) implements Cost {
        @Override
        public Result<Transaction> prepare(CostContext context) {
            return Result.success(this.transaction);
        }

        @Override
        public MapCodec<? extends Cost> codec() {
            return this.definition.codec();
        }
    }

    private record TrackedTransaction(Cost.Transaction delegate, AtomicBoolean started,
                                      AtomicBoolean uncertain) implements Cost.Transaction {
        @Override
        public CostTarget target() {
            return this.delegate.target();
        }

        @Override
        public double amount() {
            return this.delegate.amount();
        }

        @Override
        public Result<List<Debit>> execute(boolean simulate) {
            if (!simulate) this.started.set(true);
            try {
                Result<List<Debit>> result = this.delegate.execute(simulate);
                if (!simulate && result instanceof Result.FailureResult<?>) this.uncertain.set(true);
                return result;
            } catch (RuntimeException error) {
                if (!simulate) this.uncertain.set(true);
                throw error;
            }
        }

        @Override
        public Cost.Transaction withAmount(double amount) {
            return new TrackedTransaction(this.delegate.withAmount(amount), this.started, this.uncertain);
        }
    }
}
