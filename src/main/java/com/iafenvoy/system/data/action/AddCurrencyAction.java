package com.iafenvoy.system.data.action;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.assistant.economy.Wallets;
import com.iafenvoy.system.data.catalog.CurrencyDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

/** Signed whole units; a debit that exceeds the balance is refused rather than clamped. */
public record AddCurrencyAction(Holder<CurrencyDefinition> currency, long amount) implements EntityAction {
    public static final MapCodec<AddCurrencyAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CurrencyDefinition.CODEC.fieldOf("currency").forGetter(AddCurrencyAction::currency),
            Codec.LONG.fieldOf("amount").forGetter(AddCurrencyAction::amount)
    ).apply(i, AddCurrencyAction::new));

    @Override
    public @NonNull MapCodec<AddCurrencyAction> codec() {
        return CODEC;
    }

    @Override
    public void execute(EntityActionContext context) {
        if (!(context.entity() instanceof ServerPlayer player)) return;
        Identifier id = this.currency.unwrapKey().orElseThrow().identifier();
        try {
            Wallets.find(player).ifPresent(wallets -> {
                Result<Long> result = wallets.change(player, wallets.store(player), id, this.amount, false);
                // A one-shot reward must not disappear merely because another transaction currently owns the lock.
                if (result.getFailure().filter(AgentFailure.BUSY::equals).isPresent())
                    wallets.edit(player, id, this.amount, false).whenComplete((_, error) -> {
                        if (error != null) SystemMod.LOGGER.warn("Queued currency action failed for {} currency {}", player.getUUID(), id, error);
                    });
                else result.ifFailure(failure -> SystemMod.LOGGER.warn("Currency action refused for {} currency {}: {}", player.getUUID(), id, failure.name()));
            });
        } catch (AgentException error) {
            SystemMod.LOGGER.warn("Currency action refused for {} currency {}: {}", player.getUUID(), id, error.failure().name());
        }
    }
}
