package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.itemsource.ItemSource;
import com.iafenvoy.system.itemsource.ItemSourceProviders;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Shared tool/card snapshot; the client formats source pages without another server query.
 *
 * @param scope requested provider scope, retained in the tool result and saved card
 */
public record ItemSourceOutput(String item, String scope, List<ItemSource> sources,
                               boolean truncated) implements CardData {
    public static final MapCodec<ItemSourceOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(ItemSourceOutput::item),
            Codec.STRING.lenientOptionalFieldOf("scope", ItemSourceProviders.ALL).forGetter(ItemSourceOutput::scope),
            ItemSource.CODEC.listOf().fieldOf("sources").forGetter(ItemSourceOutput::sources),
            Codec.BOOL.fieldOf("truncated").forGetter(ItemSourceOutput::truncated)
    ).apply(i, ItemSourceOutput::new));

    public ItemSourceOutput {
        sources = List.copyOf(sources);
    }

    @Override
    public MapCodec<ItemSourceOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        ResourceLocation id = ResourceLocation.tryParse(this.item);
        Component name = id == null ? Component.literal(this.item) : BuiltInRegistries.ITEM.getOptional(id)
                .map(value -> Component.translatable(value.getDescriptionId())).orElseGet(() -> Component.literal(this.item));
        CardBuilder builder = CardBuilder.template(ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "item_sources"))
                .title("system.result.item_sources").summary("system.ui.item_sources_summary", name, this.sources.size())
                .put("item_id", this.item).put("truncated", this.truncated);
        return builder.list("sources", this.sources, (prefix, source) -> {
            String category = switch (source.kind()) {
                case "tag", "loot" -> source.kind();
                default -> "recipe";
            };
            builder.put(prefix + "category", category).put(prefix + "source", source).put(prefix + "id", source.id());
            for (String type : List.of("recipe", "loot", "tag")) builder.enable(prefix + type, type.equals(category));
            builder.put(prefix + "notes", CardBuilder.join(source.notes().stream().<Component>map(note -> {
                if (note.equals("requires_fuel")) return Component.translatable("system.ui.source_fuel");
                for (String key : List.of("weight", "quality", "tag"))
                    if (note.startsWith(key + " "))
                        return Component.translatable("system.ui.source_" + key + "_note", note.substring(key.length() + 1));
                return Component.literal(note);
            }).toList(), " · "));
        });
    }
}
