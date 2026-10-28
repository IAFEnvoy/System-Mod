package com.iafenvoy.system.agent;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.FailureSource;
import com.iafenvoy.saturn.runtime.resulting.ResultWithData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Saturn owns success/failure and card data; the Agent adds presentation and partial-completion state. */
public final class AgentResult extends ResultWithData<List<AgentResult.Card>> {
    public static final Codec<AgentResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("status").forGetter(AgentResult::status),
            Codec.STRING.fieldOf("messageKey").forGetter(AgentResult::messageKey),
            Codec.STRING.optionalFieldOf("text", "").forGetter(AgentResult::text),
            Card.CODEC.listOf().optionalFieldOf("cards", List.of()).forGetter(AgentResult::cards),
            Codec.BOOL.optionalFieldOf("silent", false).forGetter(AgentResult::silent)
    ).apply(i, AgentResult::new));
    private final String status, messageKey, text;
    private final boolean silent;

    public AgentResult(String status, String messageKey, String text, List<Card> cards, boolean silent) {
        super(List.copyOf(cards), status.equals("SUCCEEDED") ? null : new MessageFailure(messageKey), null);
        this.status = Objects.requireNonNull(status);
        this.messageKey = Objects.requireNonNull(messageKey);
        this.text = Objects.requireNonNull(text);
        this.silent = silent;
    }

    public AgentResult(String status, String messageKey, String text, List<Card> cards) {
        this(status, messageKey, text, cards, false);
    }

    public String status() {
        return this.status;
    }

    public String messageKey() {
        return this.messageKey;
    }

    public String text() {
        return this.text;
    }

    public List<Card> cards() {
        return this.getData();
    }

    public boolean silent() {
        return this.silent;
    }

    /** A partial result still has a failure, so it must not populate success-only route caches. */
    public AgentResult partial() {
        return new AgentResult("PARTIALLY_SUCCEEDED", this.messageKey, this.text, this.cards(), this.silent);
    }

    public AgentResult withPresentation(String key, String text, List<Card> cards, boolean silent) {
        return new AgentResult(this.status, key, text, cards, silent);
    }

    public static AgentResult ok(String key) {
        return new AgentResult("SUCCEEDED", key, "", List.of());
    }

    public static AgentResult error(String code) {
        return new AgentResult("FAILED", "system.error." + code.toLowerCase(Locale.ROOT), "", List.of());
    }

    public static AgentResult data(String key, CardData data) {
        return new AgentResult("SUCCEEDED", key, "", List.of(new Card(key, data, List.of())));
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AgentResult result && this.status.equals(result.status)
                && this.messageKey.equals(result.messageKey) && this.text.equals(result.text) && this.cards().equals(result.cards())
                && this.silent == result.silent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.status, this.messageKey, this.text, this.cards(), this.silent);
    }

    @Override
    public String toString() {
        return "AgentResult[status=" + this.status + ", messageKey=" + this.messageKey + ", text=" + this.text
                + ", cards=" + this.cards() + ", silent=" + this.silent + "]";
    }

    /** Wire snapshots keep the translated refusal key without serializing a supplier's implementation class. */
    private record MessageFailure(String key) implements Failure {
        private static final FailureSource SOURCE = new FailureSource(Identifier.fromNamespaceAndPath("system", "agent"));

        @Override
        public String name() {
            return this.key;
        }

        @Override
        public FailureSource source() {
            return SOURCE;
        }
    }

    public record Card(String titleKey, CardData data, List<Action> actions) {
        public static final Codec<Card> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("titleKey").forGetter(Card::titleKey),
                CardData.CODEC.fieldOf("data").forGetter(Card::data),
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
                TypedPayload.ACTION_CODEC.codec().fieldOf("call").forGetter(Action::call)
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
