package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/**
 * The value of a reward table entry; the id lives in the {@link PersistHolder} that owns it. The label is a component
 * so data packs can localize it; plain JSON strings decode as translation keys.
 */
public record Reward(Component label, List<ItemStackTemplate> items) {
    // Delivery reserves one main-inventory slot per template before inserting any items.
    public static final Codec<List<ItemStackTemplate>> ITEMS_CODEC = ItemStackTemplate.CODEC.listOf(1, Inventory.INVENTORY_SIZE);
    public static final MapCodec<Reward> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("label").forGetter(Reward::label),
            ITEMS_CODEC.fieldOf("items").forGetter(Reward::items)
    ).apply(i, Reward::new));

    public Reward {
        items = List.copyOf(items);
    }
}
