package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import net.minecraft.resources.Identifier;

/**
 * Built-in parameters with boundary validation.
 */
public record SignInCreditInput(String date, Identifier rewardId, String catalogRevision, boolean offline) {
    public static final SchemaMapCodec<SignInCreditInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("date", SchemaCodecs.STRING).forGetter(SignInCreditInput::date),
            f.required("rewardId", SchemaCodecs.IDENTIFIER).forGetter(SignInCreditInput::rewardId),
            f.required("catalogRevision", SchemaCodecs.STRING).forGetter(SignInCreditInput::catalogRevision),
            f.optional("offline", SchemaCodecs.BOOL, false).forGetter(SignInCreditInput::offline)
    ).apply(i, SignInCreditInput::new));

    public SignInCreditInput(String date, Identifier rewardId, String catalogRevision) {
        this(date, rewardId, catalogRevision, false);
    }
}
