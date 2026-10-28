package com.iafenvoy.system.card;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * Server-built snapshots describe cards without loading any client or optional-mod classes.
 */
public interface CardData {
    Codec<CardData> CODEC = Codec.lazyInitialized(SystemRegistries.CARD_DATA_TYPE::byNameCodec)
            .dispatch("type", CardData::codec, codec -> codec);

    MapCodec<? extends CardData> codec();

    CardBuilder buildCard();
}
