package com.iafenvoy.system.agent;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.FailureSource;
import com.iafenvoy.saturn.runtime.resulting.ResultWithData;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.data.TextCardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Saturn owns success/failure and card data; the Agent adds presentation and partial-completion state.
 */
public final class AgentResult extends ResultWithData<List<CardUpdate>> {
    public static final Codec<AgentResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            Status.CODEC.fieldOf("status").forGetter(AgentResult::status),
            Codec.STRING.fieldOf("messageKey").forGetter(AgentResult::messageKey),
            Codec.STRING.optionalFieldOf("text", "").forGetter(AgentResult::text),
            CardUpdate.CODEC.listOf().optionalFieldOf("cards", List.of()).forGetter(AgentResult::cards),
            Codec.BOOL.optionalFieldOf("silent", false).forGetter(AgentResult::silent)
    ).apply(i, AgentResult::new));
    private final Status status;
    private final String messageKey, text;
    private final boolean silent;

    public AgentResult(Status status, String messageKey, String text, List<CardUpdate> cards, boolean silent) {
        super(List.copyOf(cards), status == Status.SUCCEEDED ? null : new MessageFailure(messageKey), null);
        this.status = Objects.requireNonNull(status);
        this.messageKey = Objects.requireNonNull(messageKey);
        this.text = Objects.requireNonNull(text);
        this.silent = silent;
    }

    public AgentResult(Status status, String messageKey, String text, List<CardUpdate> cards) {
        this(status, messageKey, text, content(messageKey, text, cards, false), false);
    }

    public Status status() {
        return this.status;
    }

    public String messageKey() {
        return this.messageKey;
    }

    public String text() {
        return this.text;
    }

    public List<CardUpdate> cards() {
        return this.getData();
    }

    public boolean silent() {
        return this.silent;
    }

    /**
     * A partial result still has a failure, so it must not populate success-only route caches.
     */
    public AgentResult partial() {
        return new AgentResult(Status.PARTIALLY_SUCCEEDED, this.messageKey, this.text, this.cards(), this.silent);
    }

    public AgentResult withPresentation(String key, String text, List<CardUpdate> cards, boolean silent) {
        return new AgentResult(this.status, key, text, cards, silent);
    }

    public static AgentResult ok(String key) {
        return new AgentResult(Status.SUCCEEDED, key, "", List.of());
    }

    public static AgentResult error(String code) {
        return new AgentResult(Status.FAILED, "system.error." + code.toLowerCase(Locale.ROOT), "", List.of());
    }

    public static AgentResult data(String key, CardData data) {
        return new AgentResult(Status.SUCCEEDED, key, "", List.of(CardUpdate.show(data)));
    }

    public static AgentResult of(String key, CardData... cards) {
        return new AgentResult(Status.SUCCEEDED, key, "", Arrays.stream(cards).map(CardUpdate::show).toList());
    }

    private static List<CardUpdate> content(String key, String text, List<CardUpdate> cards, boolean silent) {
        if (!cards.isEmpty() || silent) return List.copyOf(cards);
        Component main = text.isBlank() ? Component.translatable(key)
                : Component.translatable(key).append("\n").append(text);
        return List.of(CardUpdate.show(new TextCardData(main)));
    }

    public enum Status implements StringRepresentable {
        SUCCEEDED, FAILED, PARTIALLY_SUCCEEDED;
        public static final Codec<Status> CODEC = StringRepresentable.fromEnum(Status::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
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

    /**
     * Wire snapshots keep the translated refusal key without serializing a supplier's implementation class.
     */
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

    public record Action(String labelKey, AgentAction call) {
        public static final Codec<Action> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("labelKey").forGetter(Action::labelKey),
                AgentAction.CODEC.fieldOf("call").forGetter(Action::call)
        ).apply(i, Action::new));

        public Action {
            Objects.requireNonNull(labelKey);
            Objects.requireNonNull(call);
        }

        public Identifier moduleId() {
            return this.call.id();
        }
    }
}
