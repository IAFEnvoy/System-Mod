package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

/**
 * Routing may recognize a lookup request, but only the current request supplies its question and consent.
 */
public record WebSearchInput(String question) implements AgentAction {
    public static final SchemaMapCodec<WebSearchInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("question", SchemaCodecs.STRING.validate(value -> value.isBlank()
                    ? DataResult.error(() -> "A search question is required") : DataResult.success(value))).forGetter(WebSearchInput::question)
    ).apply(i, WebSearchInput::new));

    @Override
    public MapCodec<WebSearchInput> codec() {
        return CODEC;
    }
}
