package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.screen.card.*;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.TypeDispatch;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Each payload has one parameter builder, shared by full cards, dialogue, HUD and text exports.
 */
public final class ClientCardRegistry {
    private final TypeDispatch<CardContext.Builder, Void> parameters = new TypeDispatch<>((_, _) -> null);
    private final TypeDispatch<Void, List<String>> quests = new TypeDispatch<>((_, _) -> List.of());
    private final TypeDispatch<AgentResult, String> summaries = new TypeDispatch<>((_, _) -> null);
    private final TypeDispatch<Void, Refresh> refreshes = new TypeDispatch<>((_, _) -> null);
    private final TypeDispatch<Void, String> notificationRows = new TypeDispatch<>((_, _) -> null);
    private final TypeDispatch<ResultTypes.ClientState, ResultTypes.ClientState> states = new TypeDispatch<>((previous, _) -> previous);
    private boolean frozen;

    /**
     * A silent reply replaces cards of this type and optionally keeps a notification visible.
     */
    public record Refresh(Class<?> type, AgentResult result, boolean visible) {
        public boolean matches(AgentResult previous) {
            return previous != null && previous.cards().stream().anyMatch(card -> this.type.isInstance(card.data().value()));
        }
    }

    private void checkMutable() {
        if (this.frozen) throw new IllegalStateException("Client card registration has finished");
    }

    void freeze() {
        this.frozen = true;
    }

    public <T> void register(Class<T> type, BiConsumer<CardContext.Builder, T> handler) {
        this.checkMutable();
        this.parameters.register(type, (builder, value) -> {
            handler.accept(builder, value);
            return null;
        });
    }

    public <T> void quests(Class<T> type, Function<T, List<String>> handler) {
        this.checkMutable();
        this.quests.register(type, (_, value) -> handler.apply(value));
    }

    public <T> void summary(Class<T> type, BiFunction<AgentResult, T, String> handler) {
        this.checkMutable();
        this.summaries.register(type, handler);
    }

    public <T> void notificationRows(Class<T> type, String css) {
        this.checkMutable();
        this.notificationRows.register(type, (_, _) -> css);
    }

    public String notificationRows(AgentResult.Card card) {
        return this.notificationRows.apply(null, card.data().value());
    }

    public <T> void refresh(Class<T> type, Function<T, Refresh> handler) {
        this.checkMutable();
        this.refreshes.register(type, (_, value) -> handler.apply(value));
    }

    public <T> void state(Class<T> type, BiFunction<ResultTypes.ClientState, T, ResultTypes.ClientState> handler) {
        this.checkMutable();
        this.states.register(type, handler);
    }

    public ResultTypes.ClientState state(ResultTypes.ClientState previous, AgentResult.Card card) {
        return this.states.apply(previous, card.data().value());
    }

    public String summary(AgentResult result) {
        for (AgentResult.Card card : result.cards()) {
            String summary = this.summaries.apply(result, card.data().value());
            if (summary != null) return summary;
        }
        return null;
    }

    public List<Refresh> refreshes(AgentResult result) {
        return result.cards().stream().map(card -> this.refreshes.apply(null, card.data().value()))
                .filter(Objects::nonNull).toList();
    }

    private CardContext.Builder builder(AgentResult.Card card, String messageKey, CardContext.Mode mode, boolean textItems, int actionOffset) {
        CardContext.Builder builder = CardContext.builder().put(CardContext.RESULT_ACTIONS, card.actions());
        this.parameters.apply(builder, card.data().value());
        builder.put("card_title", AssistantView.tr(card.titleKey())).put("card_type", card.data().type().id().toString())
                .enable("show_card_title", !card.titleKey().equals(messageKey)).enable("text_items", textItems)
                .enable("full", mode == CardContext.Mode.FULL).enable("simple", mode == CardContext.Mode.SIMPLE)
                .enable("notification", mode == CardContext.Mode.NOTIFICATION);
        for (int index = 0; index < card.actions().size(); index++) {
            AgentResult.Action action = card.actions().get(index);
            String id = "actions." + index + ".";
            builder.put(id + "label", AssistantView.tr(action.labelKey())).put(id + "number", actionOffset + index + 1)
                    .callback(id + "click", () -> AssistantClient.send(action, ""));
        }
        return builder;
    }

    public String format(AgentResult.Card card, boolean textItems) {
        CardPresentation presentation = this.presentation(card, "", CardContext.Mode.TEXT, textItems, 0);
        return CardRenderer.text(presentation.template().body(), presentation.template(), presentation.context(), List.of(), "\n");
    }

    public String label(AgentResult.Card card, AgentResult.Action action) {
        CardTemplate template = CardTemplates.get(card.data().type().id());
        int index = card.actions().indexOf(action);
        return template == null || index < 0 ? AssistantView.tr(action.labelKey())
                : template.label(this.builder(card, "", CardContext.Mode.TEXT, false, 0).build(), index);
    }

    public CardPresentation presentation(AgentResult.Card card, String messageKey, CardContext.Mode mode, int actionOffset) {
        return this.presentation(card, messageKey, mode, false, actionOffset);
    }

    private CardPresentation presentation(AgentResult.Card card, String messageKey, CardContext.Mode mode, boolean textItems, int actionOffset) {
        CardTemplate template = CardTemplates.get(card.data().type().id());
        CardContext.Builder builder = this.builder(card, messageKey, mode, textItems, actionOffset);
        if (template == null) {
            template = CardTemplate.fallback();
            builder.put("fallback_text", AssistantView.format(card.data().json(), 0));
        }
        return new CardPresentation(template, builder.build());
    }

    public List<String> quests(AgentResult.Card card) {
        return this.quests.apply(null, card.data().value());
    }
}
