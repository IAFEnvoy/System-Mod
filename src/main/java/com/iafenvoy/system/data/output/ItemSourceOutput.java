package com.iafenvoy.system.data.output;

import com.iafenvoy.system.itemsource.ItemSource;
import com.iafenvoy.system.itemsource.ItemSourceProviders;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Built-in result data and its serialization codec.
 *
 * @param scope echoes the request so the reply states which providers ran
 */
public record ItemSourceOutput(String item, String scope, List<ItemSource> sources, boolean truncated) {
    public static final Codec<ItemSourceOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(ItemSourceOutput::item),
            Codec.STRING.optionalFieldOf("scope", ItemSourceProviders.ALL).forGetter(ItemSourceOutput::scope),
            ItemSource.CODEC.listOf().fieldOf("sources").forGetter(ItemSourceOutput::sources),
            Codec.BOOL.fieldOf("truncated").forGetter(ItemSourceOutput::truncated)
    ).apply(i, ItemSourceOutput::new));

    public ItemSourceOutput {
        sources = List.copyOf(sources);
    }
}
