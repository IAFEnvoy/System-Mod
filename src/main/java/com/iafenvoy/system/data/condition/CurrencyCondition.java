package com.iafenvoy.system.data.condition;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.iafenvoy.system.assistant.economy.Wallets;
import com.iafenvoy.system.data.catalog.CurrencyDefinition;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;

/** A nonblocking check of already loaded module data; it neither initializes a wallet nor writes a file. */
public record CurrencyCondition(Holder<CurrencyDefinition> currency, Comparison comparison) implements EntityCondition {
    public static final MapCodec<CurrencyCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CurrencyDefinition.CODEC.fieldOf("currency").forGetter(CurrencyCondition::currency),
            Comparison.CODEC.forGetter(CurrencyCondition::comparison)
    ).apply(i, CurrencyCondition::new));

    @Override
    public MapCodec<CurrencyCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(EntityConditionContext context) {
        return context.entity() instanceof ServerPlayer player && Wallets.find(player)
                .map(wallets -> wallets.testBalance(player, this.currency.unwrapKey().orElseThrow().identifier(), this.comparison)).orElse(false);
    }
}
