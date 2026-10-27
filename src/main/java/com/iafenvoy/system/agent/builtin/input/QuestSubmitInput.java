package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;
import com.mojang.serialization.DataResult;

import java.util.Optional;

/**
 * Explicit submission selects one quest; indices use the same player-scoped page as quest details.
 */
public record QuestSubmitInput(String query, Optional<Integer> index, String questId) {
    public static final SchemaMapCodec<QuestSubmitInput> CODEC = SchemaCodecs.<QuestSubmitInput>record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING, "").forGetter(QuestSubmitInput::query),
            f.optional("index", SchemaCodecs.intRange(1, 8)).forGetter(QuestSubmitInput::index),
            f.optional("questId", SchemaCodecs.STRING, "").forGetter(QuestSubmitInput::questId)
    ).apply(i, QuestSubmitInput::new)).validate(value -> {
        int selectors = (value.query().isBlank() ? 0 : 1) + (value.index().isPresent() ? 1 : 0) + (value.questId().isBlank() ? 0 : 1);
        return selectors == 1 && (value.questId().isBlank() || value.questId().matches("[0-9a-fA-F]{16}"))
                ? DataResult.success(value) : DataResult.error(() -> "Exactly one valid quest selector is required");
    });

    public static QuestSubmitInput parse(String argument) {
        String text = argument.strip();
        return text.matches("[0-9]+") ? new QuestSubmitInput("", Optional.of(Integer.parseInt(text)), "")
                : new QuestSubmitInput(text, Optional.empty(), "");
    }
}
