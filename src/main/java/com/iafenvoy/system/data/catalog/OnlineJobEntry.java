package com.iafenvoy.system.data.catalog;

import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/** Server-owned job definition; a started run snapshots it so pack changes cannot rewrite earned output. */
public record OnlineJobEntry(Component label, Component description, long duration, List<ItemStackTemplate> items,
                              boolean enabled, int order) {
    public static final MapCodec<OnlineJobEntry> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("label").forGetter(OnlineJobEntry::label),
            ComponentSerialization.CODEC.lenientOptionalFieldOf("description", Component.empty()).forGetter(OnlineJobEntry::description),
            Codec.LONG.validate(value -> value > 0 && value <= Long.MAX_VALUE / 20
                    ? DataResult.success(value) : DataResult.error(() -> "Job duration must be positive and fit in ticks"))
                    .fieldOf("duration").forGetter(OnlineJobEntry::duration),
            Reward.ITEMS_CODEC.fieldOf("items").forGetter(OnlineJobEntry::items),
            Codec.BOOL.lenientOptionalFieldOf("enabled", true).forGetter(OnlineJobEntry::enabled),
            Codec.INT.lenientOptionalFieldOf("order", 0).forGetter(OnlineJobEntry::order)
    ).apply(i, OnlineJobEntry::new));

    public OnlineJobEntry {
        label = label.copy();
        description = description.copy();
        items = List.copyOf(items);
    }

    public long ticks() {
        return this.duration * 20;
    }

    public Reward reward() {
        return new Reward(this.label, this.items);
    }
}
