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
import net.minecraft.resources.Identifier;

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
        Identifier id = Identifier.tryParse(this.item);
        Component name = id == null ? Component.literal(this.item) : BuiltInRegistries.ITEM.getOptional(id)
                .map(value -> Component.translatable(value.getDescriptionId())).orElseGet(() -> Component.literal(this.item));
        return CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "item_sources"))
                .title("system.result.item_sources").summary("system.ui.item_sources_summary", name, this.sources.size())
                .put("item_id", this.item).put("sources", this.sources).put("truncated", this.truncated);
    }
}
