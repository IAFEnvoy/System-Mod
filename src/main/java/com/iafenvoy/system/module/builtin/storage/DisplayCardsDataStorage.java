package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.data.card.DisplayCardKey;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Core-owned pins use the existing per-world/player store, with no client configuration or optional-mod types.
 */
public final class DisplayCardsDataStorage extends DataStorage {
    public static final MapCodec<DisplayCardsDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DisplayCardKey.CODEC.listOf().lenientOptionalFieldOf("cards", List.of()).forGetter(DisplayCardsDataStorage::getCards)
    ).apply(i, DisplayCardsDataStorage::new));
    private final Set<DisplayCardKey> cards;

    public DisplayCardsDataStorage() {
        this(List.of());
    }

    private DisplayCardsDataStorage(List<DisplayCardKey> cards) {
        this.cards = new LinkedHashSet<>(cards);
    }

    public List<DisplayCardKey> getCards() {
        return List.copyOf(this.cards);
    }

    public void setPinned(DisplayCardKey key, boolean pinned) {
        if (pinned ? this.cards.add(key) : this.cards.remove(key)) this.markDirty();
    }

    @Override
    public MapCodec<DisplayCardsDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DisplayCardsDataStorage copy() {
        return new DisplayCardsDataStorage(this.getCards());
    }
}
