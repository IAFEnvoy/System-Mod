package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A pool preview is read-only; its confirmation carries a separate server-issued quote.
 */
public record LotteryPreviewInput(Identifier poolId, int offset, Optional<String> catalogRevision) {
    public static final SchemaMapCodec<LotteryPreviewInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("poolId", SchemaCodecs.IDENTIFIER).forGetter(LotteryPreviewInput::poolId),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(LotteryPreviewInput::offset),
            f.optional("catalogRevision", SchemaCodecs.STRING).forGetter(LotteryPreviewInput::catalogRevision)
    ).apply(i, LotteryPreviewInput::new));
}
