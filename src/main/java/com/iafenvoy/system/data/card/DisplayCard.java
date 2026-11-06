package com.iafenvoy.system.data.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * An unavailable source stays pinned, but exports no stale or hidden content to the client.
 */
public record DisplayCard(DisplayCardKey key, Optional<CardData> data) {
    public static final Codec<DisplayCard> CODEC = RecordCodecBuilder.create(i -> i.group(
            DisplayCardKey.CODEC.fieldOf("key").forGetter(DisplayCard::key),
            CardData.CODEC.optionalFieldOf("data").forGetter(DisplayCard::data)
    ).apply(i, DisplayCard::new));
}
