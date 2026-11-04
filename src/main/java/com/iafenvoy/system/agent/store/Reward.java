package com.iafenvoy.system.agent.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

public record Reward(Identifier id, String label, List<ItemStackTemplate> items) {
    // Delivery reserves one main-inventory slot per template before inserting any items.
    public static final Codec<List<ItemStackTemplate>> ITEMS_CODEC = ItemStackTemplate.CODEC.listOf(1, Inventory.INVENTORY_SIZE);
    public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(Reward::id),
            Codec.STRING.fieldOf("label").forGetter(Reward::label),
            ITEMS_CODEC.fieldOf("items").forGetter(Reward::items)
    ).apply(i, Reward::new));

    public Reward {
        items = List.copyOf(items);
    }
}
