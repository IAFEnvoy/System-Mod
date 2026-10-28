package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.screen.gui.AssistantView;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.Objects;

/**
 * Templates and a single parameter snapshot keep queued messages stable across resource reloads.
 */
public record CardPresentation(CardTemplate template, CardContext context) {
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
