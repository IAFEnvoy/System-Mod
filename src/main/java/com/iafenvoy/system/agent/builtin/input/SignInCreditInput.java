package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record SignInCreditInput(String date, String rewardId, String catalogRevision) {
    public static final SchemaMapCodec<SignInCreditInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("date", SchemaCodecs.STRING).forGetter(SignInCreditInput::date),
            f.required("rewardId", SchemaCodecs.STRING).forGetter(SignInCreditInput::rewardId),
            f.required("catalogRevision", SchemaCodecs.STRING).forGetter(SignInCreditInput::catalogRevision)
    ).apply(i, SignInCreditInput::new));
}
