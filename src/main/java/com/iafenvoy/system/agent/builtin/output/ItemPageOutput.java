package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Complete server snapshot; card construction only reads these fields.
 */
public record ItemPageOutput(String query, List<ItemInfoOutput> items, int nextOffset,
                             boolean hasMore) implements CardData {
    public static final MapCodec<ItemPageOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("query").forGetter(ItemPageOutput::query),
            ItemInfoOutput.CODEC.listOf().fieldOf("items").forGetter(ItemPageOutput::items),
            Codec.INT.fieldOf("nextOffset").forGetter(ItemPageOutput::nextOffset),
            Codec.BOOL.fieldOf("hasMore").forGetter(ItemPageOutput::hasMore)
    ).apply(i, ItemPageOutput::new));

    public ItemPageOutput {
        items = List.copyOf(items);
    }

    @Override
    public MapCodec<ItemPageOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "items")).title("system.result.items").summary("system.result.items")
                .list("items", this.items, (_, _) -> {
                }).put("result_items", this.items.stream().map(item -> ItemPreviewOutput.of(item.item())).toList());
        for (int i = 0; i < this.items.size(); i++) {
            ItemInfoOutput item = this.items.get(i);
            builder.put("items." + i + ".item_name", item.name()).put("items." + i + ".item_id", item.id())
                    .put("items." + i + ".max_stack", item.maxStack());
        }
        if (this.hasMore)
            builder.serverButton("next", "system.action.next", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_query"),
                    Map.of("query", this.query, "offset", Integer.toString(this.nextOffset)));
        // A single match is a specific item lookup, so its card links to the source query without retyping the ID.
        if (this.items.size() == 1)
            builder.serverButton("sources", "system.action.item_sources", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_sources"),
                    Map.of("item_id", this.items.getFirst().id()));
        return builder;
    }
}
