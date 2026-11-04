package com.iafenvoy.system.agent;

import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.card.data.TextCardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Agent adds presentation and partial-completion state to the outcome.
 * <p>
 * This is not a {@code Result}: Saturn sealed that interface to its own success and failure records, so a type that
 * carries additional fields holds them itself and answers success/failure from {@link #status}. {@code PARTIAL}
 * counts as a failure, so a partial run never populates success-only route caches.
 */
public record AgentResult(Status status, String messageKey, String text, List<CardUpdate> cards, boolean silent) {
    public static final Codec<AgentResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            Status.CODEC.fieldOf("status").forGetter(AgentResult::status),
            Codec.STRING.fieldOf("messageKey").forGetter(AgentResult::messageKey),
            Codec.STRING.optionalFieldOf("text", "").forGetter(AgentResult::text),
            CardUpdate.CODEC.listOf().optionalFieldOf("cards", List.of()).forGetter(AgentResult::cards),
            Codec.BOOL.optionalFieldOf("silent", false).forGetter(AgentResult::silent)
    ).apply(i, AgentResult::new));

    public AgentResult(Status status, String messageKey, String text, List<CardUpdate> cards, boolean silent) {
        this.status = Objects.requireNonNull(status);
        this.messageKey = Objects.requireNonNull(messageKey);
        this.text = Objects.requireNonNull(text);
        this.cards = List.copyOf(cards);
        this.silent = silent;
    }

    public AgentResult(Status status, String messageKey, String text, List<CardUpdate> cards) {
        this(status, messageKey, text, content(messageKey, text, cards, false), false);
    }

    public boolean isSuccess() {
        return this.status == Status.SUCCEEDED;
    }

    public boolean isFailure() {
        return !this.isSuccess();
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
        Component main = text.isBlank() ? Component.translatable(key) : Component.translatable(key).append("\n").append(text);
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

    public record Action(@NotNull String labelKey, @NotNull AgentAction call) {
        public static final Codec<Action> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("labelKey").forGetter(Action::labelKey),
                AgentAction.CODEC.fieldOf("call").forGetter(Action::call)
        ).apply(i, Action::new));

        public Identifier moduleId() {
            return this.call.id();
        }
    }
}
