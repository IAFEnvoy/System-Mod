package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Optional bindings group client messages, state updates, refresh rules and notification behavior.
 */
public final class ClientCardBindings {
    private final Map<Class<?>, Registration<?>> registrations = new LinkedHashMap<>();
    private final Map<String, Function<AgentResult, ClientMessage>> messages = new LinkedHashMap<>();
    private boolean frozen;

    /**
     * A silent reply replaces cards of this type and optionally keeps a notification visible.
     */
    public record Refresh(Class<?> type, AgentResult result, boolean visible) {
        public boolean matches(AgentResult previous) {
            return previous != null && previous.cards().stream().anyMatch(card -> this.type.isInstance(card.data()));
        }
    }

    private void checkMutable() {
        if (this.frozen) throw new IllegalStateException("Client card registration has finished");
    }

    void freeze() {
        this.frozen = true;
    }

    public <T> Registration<T> register(Class<T> type) {
        this.checkMutable();
        Registration<T> registration = new Registration<>(type);
        if (this.registrations.putIfAbsent(type, registration) != null)
            throw new IllegalArgumentException("Duplicate card type " + type.getName());
        return registration;
    }

    public void message(String key, Function<AgentResult, ClientMessage> handler) {
        this.checkMutable();
        if (this.messages.putIfAbsent(key, handler) != null)
            throw new IllegalArgumentException("Duplicate client message " + key);
    }

    /** Resolve the payload once so message and speech handlers share the same typed card. */
    public <T> void message(String key, Class<T> type, BiFunction<AgentResult.Card, T, ClientMessage> handler) {
        this.message(key, result -> {
            for (AgentResult.Card card : result.cards())
                if (type.isInstance(card.data()))
                    return handler.apply(card, type.cast(card.data()));
            return null;
        });
    }

    public ClientMessage message(AgentResult result) {
        Function<AgentResult, ClientMessage> handler = this.messages.get(result.messageKey());
        ClientMessage message = handler == null ? null : handler.apply(result);
        String translated = Component.translatable(result.messageKey()).getString();
        String text = translated + (result.text().isBlank() ? "" : "\n" + result.text());
        String speech = translated + (result.text().isBlank() ? "" : "\n" + result.text().strip());
        return new ClientMessage(message == null || message.text() == null ? text : message.text(),
                message == null || message.speech() == null ? speech : message.speech());
    }

    private Registration<?> registration(AgentResult.Card card) {
        return this.registrations.get(card.data().getClass());
    }

    public String notificationRows(AgentResult.Card card) {
        Registration<?> registration = this.registration(card);
        return registration == null ? null : registration.notificationRows;
    }

    public ResultTypes.ClientState state(ResultTypes.ClientState previous, AgentResult.Card card) {
        Registration<?> registration = this.registration(card);
        return registration == null ? previous : registration.state.apply(previous, card.data());
    }

    public List<Refresh> refreshes(AgentResult result) {
        return result.cards().stream().map(card -> {
            Registration<?> registration = this.registration(card);
            return registration == null ? null : registration.refresh.apply(card.data());
        }).filter(Objects::nonNull).toList();
    }

    public List<String> quests(AgentResult.Card card) {
        Registration<?> registration = this.registration(card);
        return registration == null ? List.of() : registration.quests.apply(card.data());
    }

    /** Fluent contributions stay attached to their data type and freeze with the owning bindings. */
    public final class Registration<T> {
        private final Class<T> type;
        private Function<Object, List<String>> quests = _ -> List.of();
        private Function<Object, Refresh> refresh = _ -> null;
        private String notificationRows;
        private BiFunction<ResultTypes.ClientState, Object, ResultTypes.ClientState> state = (previous, _) -> previous;

        private Registration(Class<T> type) {
            this.type = type;
        }

        public Registration<T> message(String key, BiFunction<AgentResult.Card, T, ClientMessage> handler) {
            ClientCardBindings.this.message(key, this.type, handler);
            return this;
        }

        public Registration<T> quests(Function<T, List<String>> handler) {
            ClientCardBindings.this.checkMutable();
            this.quests = value -> handler.apply(this.type.cast(value));
            return this;
        }

        public Registration<T> refresh(Function<T, AgentResult> result, Predicate<T> visible) {
            ClientCardBindings.this.checkMutable();
            this.refresh = value -> new Refresh(this.type, result.apply(this.type.cast(value)), visible.test(this.type.cast(value)));
            return this;
        }

        public Registration<T> notificationRows(String css) {
            ClientCardBindings.this.checkMutable();
            this.notificationRows = css;
            return this;
        }

        public Registration<T> state(BiFunction<ResultTypes.ClientState, T, ResultTypes.ClientState> handler) {
            ClientCardBindings.this.checkMutable();
            this.state = (previous, value) -> handler.apply(previous, this.type.cast(value));
            return this;
        }
    }
}
