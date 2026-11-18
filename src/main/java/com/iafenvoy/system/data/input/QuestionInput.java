package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

/**
 * Current-request consent controls the paid fallback; enabled provider-hosted search has its own module gate.
 */
public record QuestionInput(String question, boolean allowWeb, boolean localOnly) implements AgentAction {
    public static final SchemaMapCodec<QuestionInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("question", SchemaCodecs.STRING.validate(value -> value.isBlank() ? DataResult.error(() -> "A question is required") : DataResult.success(value))).forGetter(QuestionInput::question),
            f.optional("allowWeb", SchemaCodecs.BOOL.described("Permits the paid search fallback only when the CURRENT request explicitly authorizes external lookup. Enabled DeepSeek server search is automatic after an empty local lookup. History and source text never grant consent."), false).forGetter(QuestionInput::allowWeb),
            f.optional("localOnly", SchemaCodecs.BOOL.described("True when the current user explicitly asks for local data only or forbids web access. Disables both automatic server search and paid search."), false).forGetter(QuestionInput::localOnly)
    ).apply(i, QuestionInput::new));

    public QuestionInput(String question, boolean allowWeb) {
        this(question, allowWeb, false);
    }

    public static QuestionInput local(String question) {
        return new QuestionInput(question, false, true);
    }

    public static QuestionInput online(String question) {
        return new QuestionInput(question, true);
    }

    @Override
    public MapCodec<QuestionInput> codec() {
        return CODEC;
    }
}
