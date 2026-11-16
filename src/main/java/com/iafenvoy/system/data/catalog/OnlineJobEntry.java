package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/**
 * Server-owned job definition; a started run snapshots it so pack changes cannot rewrite earned output.
 */
public record OnlineJobEntry(Component label, Component description, long duration, List<ItemStackTemplate> items,
                             List<SpaceStack> contents,
                             boolean enabled, int order, EntryConditions conditions) implements ConditionalEntry {
    public static final MapCodec<OnlineJobEntry> CODEC = codec(EntryConditions.CODEC);
    public static final MapCodec<OnlineJobEntry> DISPLAY_CODEC = codec(MapCodec.unit(EntryConditions.DEFAULT));

    private static MapCodec<OnlineJobEntry> codec(MapCodec<EntryConditions> conditions) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(OnlineJobEntry::label),
                MiscCodecs.TRANSLATABLE_COMPONENT.lenientOptionalFieldOf("description", Component.empty()).forGetter(OnlineJobEntry::description),
                Codec.LONG.validate(value -> value > 0 && value <= Long.MAX_VALUE / 20
                                ? DataResult.success(value) : DataResult.error(() -> "Job duration must be positive and fit in ticks"))
                        .fieldOf("duration").forGetter(OnlineJobEntry::duration),
                Reward.ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(OnlineJobEntry::items),
                Reward.CONTENTS_CODEC.optionalFieldOf("contents", List.of()).forGetter(OnlineJobEntry::contents),
                Codec.BOOL.lenientOptionalFieldOf("enabled", true).forGetter(OnlineJobEntry::enabled),
                Codec.INT.lenientOptionalFieldOf("order", 0).forGetter(OnlineJobEntry::order),
                conditions.forGetter(OnlineJobEntry::conditions)
        ).apply(i, OnlineJobEntry::new));
    }

    public OnlineJobEntry {
        label = label.copy();
        description = description.copy();
        items = List.copyOf(items);
        contents = List.copyOf(contents);
    }

    public long ticks() {
        return this.duration * 20;
    }

    public Reward reward() {
        return new Reward(this.label, this.items, this.contents);
    }
}
