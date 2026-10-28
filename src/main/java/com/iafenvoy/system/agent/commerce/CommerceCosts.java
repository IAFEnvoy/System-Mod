package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.CostPayment;
import com.iafenvoy.saturn.data.cost.CostTarget;
import com.iafenvoy.saturn.data.cost.Debit;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.ResultWithData;
import com.iafenvoy.saturn.runtime.resulting.failures.CostFailure;
import com.iafenvoy.saturn.util.DefinitionText;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.saturn.util.matcher.builtin.ItemEntry;
import com.iafenvoy.saturn.util.matcher.builtin.TagEntry;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

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
            ResultWithData<Cost.Transaction> result = cost.prepare(context);
            if (result.isFailure()) {
                failure = result.failure();
                failedResource = result.failedResource();
                break;
            }
            Cost.Transaction transaction = result.getData();
            if (!Double.isFinite(transaction.amount()) || transaction.amount() < 0) {
                failure = CostFailure.INVALID_AMOUNT;
                break;
            }
            Optional<Failure> pricingFailure = bill.price(List.of(new BoundCost(cost, new TrackedTransaction(transaction, started, uncertain))));
            if (pricingFailure.isPresent()) {
                failure = pricingFailure.get();
                failedResource = bill.failedResource();
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
        if (failure != null) descriptions.add(failure.message(failedResource));
        return new Prepared(player, bill, new CommerceCatalog.Price(items, descriptions), failure, failedResource, started, uncertain);
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

    /** The result owns the immutable display price; the bound bill stays server-side for confirmation. */
    public static final class Prepared extends ResultWithData<CommerceCatalog.Price> {
        private final ServerPlayer payer;
        private final CostPayment bill;
        private final AtomicBoolean started, uncertain;

        private Prepared(ServerPlayer payer, CostPayment bill, CommerceCatalog.Price price, @Nullable Failure failure,
                         @Nullable Identifier failedResource, AtomicBoolean started, AtomicBoolean uncertain) {
            super(price, failure, failedResource);
            this.payer = payer;
            this.bill = bill;
            this.started = started;
            this.uncertain = uncertain;
        }

        public void pay(ServerPlayer player, AtomicBoolean touched) {
            if (player != this.payer) throw new AgentException("STALE_QUOTE");
            if (this.isFailure()) throw new AgentException("INVALID_COST");
            if (this.bill.test().isPresent()) throw new AgentException("INSUFFICIENT_COST");
            CostPayment.PaymentResult result;
            try {
                result = this.bill.pay();
            } finally {
                touched.set(this.started.get());
            }
            // Saturn continues real fees after an error; never unlock goods for a partial or uncertain collection.
            if (this.uncertain.get()) throw new AgentException("EFFECT_UNKNOWN");
            if (result.isFailure()) {
                throw new AgentException("INSUFFICIENT_COST");
            }
        }
    }

    private record BoundCost(Cost definition, Cost.Transaction transaction) implements Cost {
        @Override
        public ResultWithData<Transaction> prepare(CostContext context) {
            return ResultWithData.success(this.transaction);
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
        public ResultWithData<List<Debit>> execute(boolean simulate) {
            if (!simulate) this.started.set(true);
            try {
                ResultWithData<List<Debit>> result = this.delegate.execute(simulate);
                if (!simulate && result.isFailure()) this.uncertain.set(true);
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
