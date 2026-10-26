package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;

/** Built-in parameters with boundary validation. */
public record SignInCreditInput(String date, String rewardId, String catalogRevision) {
    public static final SchemaMapCodec<SignInCreditInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("date", SchemaCodecs.string(0, 2000)).forGetter(SignInCreditInput::date),
            f.required("rewardId", SchemaCodecs.string(0, 2000)).forGetter(SignInCreditInput::rewardId),
            f.required("catalogRevision", SchemaCodecs.string(0, 2000)).forGetter(SignInCreditInput::catalogRevision)
    ).apply(i, SignInCreditInput::new));
}
