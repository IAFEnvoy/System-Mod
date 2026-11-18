package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Complete server snapshot; card construction only reads these fields.
 */
public record ItemPageOutput(String query, List<ItemInfoOutput> items, int nextOffset,
                             boolean hasMore, Optional<String> searchRef, int offset,
                             Optional<String> sourceScope) implements CardData {
    public static final MapCodec<ItemPageOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("query").forGetter(ItemPageOutput::query),
            ItemInfoOutput.CODEC.listOf().fieldOf("items").forGetter(ItemPageOutput::items),
            Codec.INT.fieldOf("nextOffset").forGetter(ItemPageOutput::nextOffset),
            Codec.BOOL.fieldOf("hasMore").forGetter(ItemPageOutput::hasMore),
            Codec.STRING.optionalFieldOf("searchRef").forGetter(ItemPageOutput::searchRef),
            Codec.INT.optionalFieldOf("offset", 0).forGetter(ItemPageOutput::offset),
            Codec.STRING.optionalFieldOf("sourceScope").forGetter(ItemPageOutput::sourceScope)
    ).apply(i, ItemPageOutput::new));

    public ItemPageOutput {
        items = List.copyOf(items);
    }

    public ItemPageOutput(String query, List<ItemInfoOutput> items, int nextOffset, boolean hasMore) {
        this(query, items, nextOffset, hasMore, Optional.empty(), 0, Optional.empty());
    }

    public ItemPageOutput(String query, List<ItemInfoOutput> items, int nextOffset, boolean hasMore, Optional<String> searchRef, int offset) {
        this(query, items, nextOffset, hasMore, searchRef, offset, Optional.empty());
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
                            Map.of("item_id", this.items.getFirst().id(), "scope", this.sourceScope.orElse("all")), CardButton.Placement.FOOTER, CardButton.Behavior.PUSH)
                    .actionParameter("footer", true);
        else
            // Item indices and action indices stay aligned, including when the client grid changes pages.
            for (int i = 0; i < this.items.size(); i++) {
                ItemInfoOutput item = this.items.get(i);
                if (this.sourceScope.isPresent()) {
                    builder.serverButton("item." + i, "system.action.item_sources", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_sources"),
                                    Map.of("item_id", item.id(), "scope", this.sourceScope.get()), CardButton.Placement.BODY, CardButton.Behavior.PUSH)
                            .actionParameter("label", item.name());
                    continue;
                }
                Map<String, String> arguments = new LinkedHashMap<>();
                arguments.put("query", this.searchRef.isPresent() ? this.query : item.id());
                if (this.searchRef.isPresent()) {
                    arguments.put("search_ref", this.searchRef.get());
                    arguments.put("offset", Integer.toString(this.offset));
                    arguments.put("selected", Integer.toString(i));
                }
                builder.serverButton("item." + i, "system.action.view_item_info", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_query"),
                                arguments, CardButton.Placement.BODY, CardButton.Behavior.PUSH)
                        .actionParameter("label", item.name());
            }
        if (this.hasMore)
            builder.serverButton("next", "system.action.more_items", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, this.sourceScope.isPresent() ? "item_sources" : "item_query"),
                            this.sourceScope.map(s -> Map.of("item_id", this.query, "scope", s, "offset", Integer.toString(this.nextOffset)))
                                    .orElseGet(() -> Map.of("query", this.query, "offset", Integer.toString(this.nextOffset))))
                    .actionParameter("more_items", true).actionParameter("footer", true);
        return builder;
    }
}
