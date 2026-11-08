package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

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
        CardBuilder builder = CardBuilder.template(ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "items"))
                .title("system.result.items").summary("system.result.items");
        builder.list("items", this.items, (prefix, item) -> builder.put(prefix + "item_name", item.name())
                .put(prefix + "item_id", item.id()).put(prefix + "max_stack", item.maxStack())
                .put(prefix + "preview", ItemPreviewOutput.of(item.item())));
        // Item indices and action indices stay aligned, including when the client grid changes pages.
        for (int i = 0; i < this.items.size(); i++) {
            ItemInfoOutput item = this.items.get(i);
            builder.serverButton("item." + i, "system.action.item_sources", ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "item_sources"),
                            Map.of("item_id", item.id()), CardButton.Placement.BODY, CardButton.Behavior.PUSH)
                    .actionParameter("label", item.name());
        }
        if (this.hasMore)
            builder.serverButton("next", "system.action.more_items", ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "item_query"),
                            Map.of("query", this.query, "offset", Integer.toString(this.nextOffset)))
                    .actionParameter("more_items", true);
        return builder;
    }
}
