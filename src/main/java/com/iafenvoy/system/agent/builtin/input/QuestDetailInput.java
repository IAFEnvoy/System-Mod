package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;
import com.mojang.serialization.DataResult;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record QuestDetailInput(String query, Optional<Integer> index, String questId, int offset,
                               int rewardOffset, int choiceOffset, int descriptionOffset, int taskOffset) {
    public static final SchemaMapCodec<QuestDetailInput> CODEC = SchemaCodecs.<QuestDetailInput>record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING.described("Quest name or ID; one of query, index and questId must select a quest."), "").forGetter(QuestDetailInput::query),
            f.optional("index", SchemaCodecs.intRange(1, 8).described("One-based index on the player's last quest page.")).forGetter(QuestDetailInput::index),
            f.optional("questId", SchemaCodecs.STRING.described("Known 16-digit hexadecimal quest ID; empty means unused."), "").forGetter(QuestDetailInput::questId),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(QuestDetailInput::offset),
            f.optional("rewardOffset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(QuestDetailInput::rewardOffset),
            f.optional("choiceOffset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(QuestDetailInput::choiceOffset),
            f.optional("descriptionOffset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(QuestDetailInput::descriptionOffset),
            f.optional("taskOffset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(QuestDetailInput::taskOffset)
    ).apply(i, QuestDetailInput::new)).validate(value -> {
        int selectors = (value.query().isBlank() ? 0 : 1) + (value.index().isPresent() ? 1 : 0) + (value.questId().isBlank() ? 0 : 1);
        return selectors != 1 || !value.questId().isBlank() && !value.questId().matches("[0-9a-fA-F]{16}")
                ? DataResult.error(() -> "Exactly one quest selector is required") : DataResult.success(value);
    }).described("Supply exactly one nonblank query, index or nonblank questId. Never invent quest IDs or page indices.");

    public static QuestDetailInput byId(String id) {
        return new QuestDetailInput("", Optional.empty(), id, 0, 0, 0, 0, 0);
    }
}
