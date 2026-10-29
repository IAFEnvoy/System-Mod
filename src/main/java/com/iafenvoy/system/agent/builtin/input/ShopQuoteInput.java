package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record ShopQuoteInput(ResourceLocation offerId, Optional<String> catalogRevision) implements AgentAction {
    public static final SchemaMapCodec<ShopQuoteInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("offerId", SchemaCodecs.IDENTIFIER).forGetter(ShopQuoteInput::offerId),
            f.optional("catalogRevision", SchemaCodecs.STRING).forGetter(ShopQuoteInput::catalogRevision)
    ).apply(i, ShopQuoteInput::new));

    @Override
    public MapCodec<ShopQuoteInput> codec() {
        return CODEC;
    }
}
