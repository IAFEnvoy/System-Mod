package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
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
        boolean singleItem = this.items.size() == 1;
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "items"))
                .title("system.result.items").summary("system.result.items").enable("single_item", singleItem);
        builder.list("items", this.items, (prefix, item) -> builder.put(prefix + "item_name", item.name())
                .put(prefix + "item_id", item.id()).put(prefix + "max_stack", item.maxStack())
                .put(prefix + "preview", ItemPreviewOutput.of(item.item())));
        if (singleItem)
            builder.serverButton("sources", "system.action.item_sources", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_sources"),
                            Map.of("item_id", this.items.getFirst().id()), CardButton.Placement.FOOTER, CardButton.Behavior.PUSH)
                    .actionParameter("footer", true);
        else
            // Item indices and action indices stay aligned, including when the client grid changes pages.
            for (int i = 0; i < this.items.size(); i++) {
                ItemInfoOutput item = this.items.get(i);
                builder.serverButton("item." + i, "system.action.view_item_info", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_query"),
                                Map.of("query", item.id()), CardButton.Placement.BODY, CardButton.Behavior.PUSH)
                        .actionParameter("label", item.name());
            }
        if (this.hasMore)
            builder.serverButton("next", "system.action.more_items", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_query"),
                            Map.of("query", this.query, "offset", Integer.toString(this.nextOffset)))
                    .actionParameter("more_items", true).actionParameter("footer", true);
        return builder;
    }
}
