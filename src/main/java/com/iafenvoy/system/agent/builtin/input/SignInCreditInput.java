package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;
import net.minecraft.resources.Identifier;

/**
 * Built-in parameters with boundary validation.
 */
public record SignInCreditInput(String date, Identifier rewardId, String catalogRevision) {
    public static final SchemaMapCodec<SignInCreditInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("date", SchemaCodecs.STRING).forGetter(SignInCreditInput::date),
            f.required("rewardId", SchemaCodecs.IDENTIFIER).forGetter(SignInCreditInput::rewardId),
            f.required("catalogRevision", SchemaCodecs.STRING).forGetter(SignInCreditInput::catalogRevision)
    ).apply(i, SignInCreditInput::new));
}
