package com.iafenvoy.system.agent;

import com.iafenvoy.assistant.AgentResult.Status;
import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.card.data.TextCardData;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * System projects workflow results and Saturn failures into its own card types.
 */
public final class SystemResults {
    public static final Codec<AgentResult<CardUpdate>> CODEC = AgentResult.codec(CardUpdate.CODEC);

    private SystemResults() {
    }

    public static AgentResult<CardUpdate> presented(Status status, String key, String text, List<CardUpdate> cards) {
        return new AgentResult<>(status, key, text, content(key, text, cards), false);
    }

    public static AgentResult<CardUpdate> ok(String key) {
        return presented(Status.SUCCEEDED, key, "", List.of());
    }

    public static AgentResult<CardUpdate> error(Failure failure) {
        return presented(Status.FAILED, failure.key(), "", List.of(CardUpdate.show(new TextCardData(failure.message()))));
    }

    public static AgentResult<CardUpdate> data(String key, CardData data) {
        return presented(Status.SUCCEEDED, key, "", List.of(CardUpdate.show(data)));
    }

    public static AgentResult<CardUpdate> of(String key, CardData... cards) {
        return presented(Status.SUCCEEDED, key, "", Arrays.stream(cards).map(CardUpdate::show).toList());
    }

    private static List<CardUpdate> content(String key, String text, List<CardUpdate> cards) {
        if (!cards.isEmpty()) return List.copyOf(cards);
        Component main = text.isBlank() ? Component.translatable(key) : Component.translatable(key).append("\n").append(text);
        return List.of(CardUpdate.show(new TextCardData(main)));
    }

}
