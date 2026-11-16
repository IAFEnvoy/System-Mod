package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.space.ItemSpaceContent;
import com.iafenvoy.system.data.space.SpaceStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.stream.Stream;

/**
 * The value of a reward table entry; the id lives in the {@link PersistHolder} that owns it. The label is a component
 * so data packs can localize it; plain JSON strings decode as translation keys.
 */
public record Reward(Component label, List<ItemStackTemplate> items, List<SpaceStack> contents) {
    public static final Codec<List<ItemStackTemplate>> ITEMS_CODEC = ItemStackTemplate.CODEC.listOf(0, Inventory.INVENTORY_SIZE);
    public static final Codec<List<SpaceStack>> CONTENTS_CODEC = SpaceStack.CODEC.listOf(0, Inventory.INVENTORY_SIZE);
    public static final MapCodec<Reward> CODEC = codec(CONTENTS_CODEC);
    // A settled multi-draw can combine more content kinds than an individual data-pack reward.
    public static final MapCodec<Reward> SNAPSHOT_CODEC = codec(SpaceStack.CODEC.listOf(0, 4096));

    private static MapCodec<Reward> codec(Codec<List<SpaceStack>> contents) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(Reward::label),
                ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(Reward::items),
                contents.optionalFieldOf("contents", List.of()).forGetter(Reward::contents)
        ).apply(i, Reward::new));
    }

    public Reward {
        label = label.copy();
        items = List.copyOf(items);
        contents = List.copyOf(contents);
    }

    public Reward(Component label, List<ItemStackTemplate> items) {
        this(label, items, List.of());
    }

    public List<SpaceStack> stacks() {
        return Stream.concat(this.items.stream().map(item -> new SpaceStack(new ItemSpaceContent(item), item.count())),
                this.contents.stream()).toList();
    }
}
