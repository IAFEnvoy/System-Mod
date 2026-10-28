package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in result data and its serialization codec.
 */
public record ItemPageOutput(List<ItemInfoOutput> items, int nextOffset, boolean hasMore) implements CardData {
    public static final MapCodec<ItemPageOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemInfoOutput.CODEC.listOf().fieldOf("items").forGetter(ItemPageOutput::items),
            Codec.INT.fieldOf("nextOffset").forGetter(ItemPageOutput::nextOffset),
            Codec.BOOL.fieldOf("hasMore").forGetter(ItemPageOutput::hasMore)
    ).apply(i, ItemPageOutput::new));
    public static final Identifier TEMPLATE = Identifier.parse("system:items");

    public ItemPageOutput {
        items = List.copyOf(items);
    }

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        builder.list("items", this.items(), (prefix, item) -> builder.put(prefix + "item_name", item.name())
                .put(prefix + "item_id", item.id()).put(prefix + "max_stack", item.maxStack()));
        List<ItemPreviewOutput> previews = new ArrayList<>();
        for (ItemInfoOutput entry : this.items()) {
            Identifier id = Identifier.tryParse(entry.id());
            if (id == null) continue;
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                if (!new ItemStack(item).isEmpty())
                    previews.add(ItemPreviewOutput.of(new ItemStackTemplate(item)));
            });
        }
        builder.put("result_items", previews);
    }

    @Override
    public MapCodec<ItemPageOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
