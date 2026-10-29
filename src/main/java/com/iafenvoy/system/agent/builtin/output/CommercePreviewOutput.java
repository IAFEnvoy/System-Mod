package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Display-only evaluated price; the optional reference identifies a server-owned bound payment.
 */
public record CommercePreviewOutput(ResourceLocation source, CommerceCatalog.Price price, boolean available,
                                    Optional<String> quoteRef) {
    public static final Codec<CommercePreviewOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("source").forGetter(CommercePreviewOutput::source),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(CommercePreviewOutput::price),
            Codec.BOOL.fieldOf("available").forGetter(CommercePreviewOutput::available),
            Codec.STRING.optionalFieldOf("quoteRef").forGetter(CommercePreviewOutput::quoteRef)
    ).apply(i, CommercePreviewOutput::new));
}
