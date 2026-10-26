package com.iafenvoy.system.agent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record AgentResult(String status, String messageKey, String text, List<Card> cards) {
    public static final Codec<AgentResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("status").forGetter(AgentResult::status),
            Codec.STRING.fieldOf("messageKey").forGetter(AgentResult::messageKey),
            Codec.STRING.optionalFieldOf("text", "").forGetter(AgentResult::text),
            Card.CODEC.listOf().optionalFieldOf("cards", List.of()).forGetter(AgentResult::cards)
    ).apply(i, AgentResult::new));

    public AgentResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(messageKey);
        Objects.requireNonNull(text);
        cards = List.copyOf(cards);
    }

    public static AgentResult ok(String key) {
        return new AgentResult("SUCCEEDED", key, "", List.of());
    }

    public static AgentResult error(String code) {
        return new AgentResult("FAILED", "system.error." + code.toLowerCase(Locale.ROOT), "", List.of());
    }

    public static AgentResult data(String key, TypedPayload<?> data) {
        return new AgentResult("SUCCEEDED", key, "", List.of(new Card(key, data, List.of())));
    }

    public record Card(String titleKey, TypedPayload<?> data, List<Action> actions) {
        public static final Codec<Card> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("titleKey").forGetter(Card::titleKey),
                ResultTypes.CARDS.codec().fieldOf("data").forGetter(Card::data),
                Action.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(Card::actions)
        ).apply(i, Card::new));

        public Card {
            Objects.requireNonNull(titleKey);
            Objects.requireNonNull(data);
            actions = List.copyOf(actions);
        }
    }

    public record Action(String labelKey, TypedPayload<?> call) {
        public static final Codec<Action> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("labelKey").forGetter(Action::labelKey),
                ResultTypes.ACTIONS.codec().fieldOf("call").forGetter(Action::call)
        ).apply(i, Action::new));

        public Action {
            Objects.requireNonNull(labelKey);
            Objects.requireNonNull(call);
        }

        public <T> Action(String labelKey, TypedPayload.Type<T> type, T input) {
            this(labelKey, type.of(input));
        }

        public Identifier moduleId() {
            return this.call.type().id();
        }
    }
}
