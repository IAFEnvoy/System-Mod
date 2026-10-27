package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.DataResult;

import java.util.Optional;

/**
 * Explicit submission selects one quest; indices use the same player-scoped list as quest details.
 */
public record QuestSubmitInput(String query, Optional<Integer> index, String questId, boolean checkmarkOnly) {
    public static final SchemaMapCodec<QuestSubmitInput> CODEC = SchemaCodecs.<QuestSubmitInput>record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING, "").forGetter(QuestSubmitInput::query),
            f.optional("index", SchemaCodecs.intRange(1, Integer.MAX_VALUE)).forGetter(QuestSubmitInput::index),
            f.optional("questId", SchemaCodecs.STRING, "").forGetter(QuestSubmitInput::questId),
            f.optional("checkmarkOnly", SchemaCodecs.BOOL, false).forGetter(QuestSubmitInput::checkmarkOnly)
    ).apply(i, QuestSubmitInput::new)).validate(value -> {
        int selectors = (value.query().isBlank() ? 0 : 1) + (value.index().isPresent() ? 1 : 0) + (value.questId().isBlank() ? 0 : 1);
        return selectors == 1 && (value.questId().isBlank() || value.questId().matches("[0-9a-fA-F]{16}"))
                ? DataResult.success(value) : DataResult.error(() -> "Exactly one valid quest selector is required");
    });

    public QuestSubmitInput(String query, Optional<Integer> index, String questId) {
        this(query, index, questId, false);
    }

    public static QuestSubmitInput complete(String questId) {
        return new QuestSubmitInput("", Optional.empty(), questId, true);
    }

    public static QuestSubmitInput parse(String argument) {
        String text = argument.strip();
        return text.matches("[0-9]+") ? new QuestSubmitInput("", Optional.of(Integer.parseInt(text)), "")
                : new QuestSubmitInput(text, Optional.empty(), "");
    }
}
