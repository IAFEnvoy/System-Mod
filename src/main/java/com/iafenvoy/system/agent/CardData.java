package com.iafenvoy.system.agent;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

/**
 * Shared card data owns serialization, template ID and parameters, never client rendering code.
 */
public interface CardData {
    Codec<CardData> CODEC = SystemRegistries.CARD_DATA_TYPE.byNameCodec().dispatch("type", CardData::codec, codec -> codec);

    MapCodec<? extends CardData> codec();

    /**
     * Identifies a client layout at {@code assets/<namespace>/cards/<path>.json}.
     */
    Identifier template();

    /**
     * Fill type-specific parameters; the caller supplies actions before this and UI callbacks afterwards.
     */
    default void buildContext(CardContext.Builder builder, CardFormatting formatting) {
    }
}
