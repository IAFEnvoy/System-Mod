package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Read-only pricing selects trusted catalog entries; callers never supply executable costs.
 */
public record CommercePreviewInput(List<ResourceLocation> offers, List<ResourceLocation> lotteries, boolean quote,
                                   String revision) {
    public static final SchemaMapCodec<CommercePreviewInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("offers", SchemaCodecs.IDENTIFIER.listOf()).forGetter(CommercePreviewInput::offers),
            f.required("lotteries", SchemaCodecs.IDENTIFIER.listOf()).forGetter(CommercePreviewInput::lotteries),
            f.required("quote", SchemaCodecs.BOOL).forGetter(CommercePreviewInput::quote),
            f.required("revision", SchemaCodecs.STRING).forGetter(CommercePreviewInput::revision)
    ).apply(i, CommercePreviewInput::new));

    public CommercePreviewInput {
        offers = List.copyOf(offers);
        lotteries = List.copyOf(lotteries);
    }
}
