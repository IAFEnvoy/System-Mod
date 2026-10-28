package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.JsonOps;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client presentation assembles context and resolves layouts without requiring behavior bindings.
 */
public record CardPresentation(CardTemplate template, CardContext context) {
    /** Data fills its parameters between the shared action input and client-owned controls. */
    public static CardContext context(AgentResult.Card card, String messageKey, CardContext.Mode mode, boolean textItems, int actionOffset) {
        CardContext.Builder builder = CardContext.builder().put(CardContext.RESULT_ACTIONS, card.actions());
        card.data().buildContext(builder, Component::getString);
        builder.put("card_title", AssistantView.tr(card.titleKey())).put("card_template", card.data().template().toString())
                .enable("show_card_title", !card.titleKey().equals(messageKey)).enable("text_items", textItems)
                .enable("full", mode == CardContext.Mode.FULL).enable("simple", mode == CardContext.Mode.SIMPLE)
                .enable("notification", mode == CardContext.Mode.NOTIFICATION);
        for (int index = 0; index < card.actions().size(); index++) {
            AgentResult.Action action = card.actions().get(index);
            String id = "actions." + index + ".";
            builder.put(id + "label", AssistantView.tr(action.labelKey())).put(id + "number", actionOffset + index + 1)
                    .callback(id + "click", () -> AssistantClient.send(action, ""));
        }
        return builder.build();
    }

    public static CardPresentation of(AgentResult.Card card, String messageKey, CardContext.Mode mode, int actionOffset) {
        return of(card.data(), context(card, messageKey, mode, false, actionOffset));
    }

    /** Resolve the current layout once; queued messages retain this template and context snapshot. */
    public static CardPresentation of(CardData data, CardContext context) {
        CardTemplate template = CardTemplates.get(data.template());
        if (template != null) return new CardPresentation(template, context);
        // Fallback text belongs to presentation and must not modify the supplied business context.
        Map<String, Object> parameters = new LinkedHashMap<>(context.parameters());
        parameters.put("fallback_text", AssistantView.format(CardData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(), 0));
        return new CardPresentation(CardTemplate.fallback(), new CardContext(context.states(), parameters, context.callbacks()));
    }

    public static String format(AgentResult.Card card, boolean textItems) {
        CardPresentation presentation = of(card.data(), context(card, "", CardContext.Mode.TEXT, textItems, 0));
        return CardRenderer.text(presentation.template().body(), presentation.template(), presentation.context(), List.of(), "\n");
    }

    public static String label(AgentResult.Card card, AgentResult.Action action) {
        int index = card.actions().indexOf(action);
        if (index < 0) return AssistantView.tr(action.labelKey());
        CardPresentation presentation = of(card, "", CardContext.Mode.TEXT, 0);
        return presentation.template().label(presentation.context(), index);
    }

    public Element render(Document document) {
        Element result = AssistantView.element(document, "div", this.template.css(), "");
        if (this.template.showTitle() && CardRenderer.enabled("show_card_title", this.context, List.of()))
            result.appendChild(AssistantView.element(document, "div", "card_title", CardRenderer.value("{{card_title}}", this.context, List.of())));
        CardRenderer.render(document, result, this.template.body(), this.template, this.context, List.of());
        return result;
    }

    // Callback instances are not visible content; rebuilding them must not bypass notification deduplication.
    @Override
    public boolean equals(Object other) {
        return other instanceof CardPresentation(
                CardTemplate template1, CardContext context1
        ) && this.template.equals(template1)
                && this.context.states().equals(context1.states()) && this.context.parameters().equals(context1.parameters())
                && this.context.callbacks().keySet().equals(context1.callbacks().keySet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.template, this.context.states(), this.context.parameters(), this.context.callbacks().keySet());
    }
}
