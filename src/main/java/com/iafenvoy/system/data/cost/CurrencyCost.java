package com.iafenvoy.system.data.cost;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.CostTarget;
import com.iafenvoy.saturn.data.cost.CostTransaction;
import com.iafenvoy.saturn.data.cost.Debit;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.runtime.resulting.failures.CostFailure;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.assistant.economy.Wallets;
import com.iafenvoy.system.data.catalog.CurrencyDefinition;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/** Saturn's payment lifecycle and re-entrancy guard, with System's independent module wallet as its channel. */
public record CurrencyCost(Holder<CurrencyDefinition> currency, NumberProvider amount) implements Cost {
    public static final MapCodec<CurrencyCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CurrencyDefinition.CODEC.fieldOf("currency").forGetter(CurrencyCost::currency),
            NumberProvider.CODEC.fieldOf("amount").forGetter(CurrencyCost::amount)
    ).apply(i, CurrencyCost::new));

    @Override
    public Result<Cost.Transaction> prepare(CostContext context) {
        if (!(context.payer() instanceof ServerPlayer player)) return Result.failure(CostFailure.NO_CHANNEL);
        Wallets wallets = Wallets.find(player).filter(service -> service.available(player)).orElse(null);
        if (wallets == null) return Result.failure(CostFailure.NO_CHANNEL);
        double amount = this.amount.evaluate(context.formula());
        if (!valid(amount)) return Result.failure(CostFailure.INVALID_AMOUNT);
        return Result.success(new Transaction(wallets, player, wallets.store(player),
                this.currency.unwrapKey().orElseThrow().identifier(), amount));
    }

    private static boolean valid(double value) {
        // Cost amounts are doubles; only exactly representable whole units can cross that boundary.
        return Double.isFinite(value) && value >= 0 && value <= 9007199254740991D && value == Math.rint(value);
    }

    @Override
    public MapCodec<CurrencyCost> codec() {
        return CODEC;
    }

    public record Target(UUID player, Identifier currency) implements CostTarget {
        @Override
        public String label() {
            return this.currency.toString();
        }
    }

    private static final class Transaction extends CostTransaction {
        private final Wallets wallets;
        private final ServerPlayer player;
        private final AgentStore store;
        private final Identifier currency;

        private Transaction(Wallets wallets, ServerPlayer player, AgentStore store, Identifier currency, double amount) {
            super(new Target(player.getUUID(), currency), amount);
            this.wallets = wallets;
            this.player = player;
            this.store = store;
            this.currency = currency;
        }

        @Override
        protected Result<List<Debit>> perform(boolean simulate) {
            if (!valid(this.amount())) return refused(CostFailure.INVALID_AMOUNT);
            try {
                Result<Long> result = this.wallets.change(this.player, this.store, this.currency, -(long) this.amount(), simulate);
                if (result.getFailure().isPresent()) return refused(result.getFailure().orElseThrow());
                return took(simulate ? List.of() : List.of(Debit.of(this.target(), this.amount())));
            } catch (AgentException error) {
                return refused(error.failure());
            }
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new Transaction(this.wallets, this.player, this.store, this.currency, amount);
        }
    }
}
