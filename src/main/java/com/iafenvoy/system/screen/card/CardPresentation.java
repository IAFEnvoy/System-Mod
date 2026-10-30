package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.card.*;
import com.iafenvoy.system.card.client.CardController;
import com.iafenvoy.system.card.client.CardFormatting;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.module.client.ClientMessage;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.card.node.ActionsNode;
import com.iafenvoy.system.screen.card.node.TextNode;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single client construction path resolves local text, templates and trusted button callbacks.
 */
public record CardPresentation(CardTemplate template, CardContext context) {
    private static final CardFormatting FORMAT = LegacyText::formatted;

    public static CardPresentation of(CardUpdate original, String messageKey, CardContext.Mode mode, int actionOffset) {
        return of(original, messageKey, mode, actionOffset, new CardPageState());
    }

    public static CardPresentation of(CardUpdate original, String messageKey, CardContext.Mode mode, int actionOffset, CardPageState pages) {
        CardUpdate update = ClientCards.latest(original);
        CardView view = ClientCards.view(update);
        CardContext.Builder builder = CardContext.builder();
        view.context().parameters().forEach((key, value) -> builder.put(key, value instanceof Component || value instanceof CardText ? FORMAT.value(value) : value));
        view.context().states().forEach((key, value) -> {
            Object parameter = view.context().parameters().get(key);
            if (!(parameter instanceof Component) && !(parameter instanceof CardText)) builder.enable(key, value);
        });
        builder.put("summary", FORMAT.text(view.summary())).put("card_title", view.titleKey().isBlank() ? "" : AssistantView.tr(view.titleKey()))
                .enable("show_card_title", !view.titleKey().isBlank() && !view.titleKey().equals(messageKey))
                .enable("text_items", mode == CardContext.Mode.TEXT)
                .enable("full", mode == CardContext.Mode.FULL).enable("simple", mode == CardContext.Mode.SIMPLE)
                .enable("notification", mode == CardContext.Mode.NOTIFICATION).put(CardContext.RESULT_ACTIONS, view.buttons())
                .put("local_pages", pages);
        for (int i = 0; i < view.buttons().size(); i++) {
            CardButton button = view.buttons().get(i);
            String prefix = "actions." + i + ".";
            builder.put(prefix + "label", AssistantView.tr(button.labelKey())).put(prefix + "number", actionOffset + i + 1);
            if (ClientCards.active(update.ref())) builder.callback(prefix + "click", () -> {
                if (mode != CardContext.Mode.NOTIFICATION || AssistantMessages.canActivate(update.ref()))
                    CardController.activate(update.ref(), button, mode == CardContext.Mode.NOTIFICATION);
            });
        }
        CardTemplate template = CardTemplates.get(view.template());
        if (template == null) template = CardTemplate.fallback();
        return new CardPresentation(template, builder.build());
    }

    public static String format(CardUpdate update, boolean textItems) {
        CardPresentation card = of(update, "", CardContext.Mode.TEXT, 0);
        return CardRenderer.text(card.template.body(), card.template, card.context, List.of(), "\n");
    }

    public static String label(CardUpdate update, CardButton button) {
        int index = ClientCards.view(update).buttons().indexOf(button);
        CardPresentation presentation = of(update, "", CardContext.Mode.TEXT, 0);
        return index < 0 ? AssistantView.tr(button.labelKey()) : presentation.template.label(presentation.context, index);
    }

    public static ClientMessage message(AgentResult result) {
        List<CardView> cards = result.cards().stream().filter(card -> card.data().isPresent()).map(ClientCards::view).toList();
        String text = String.join("\n", cards.stream().map(card -> card.summary().getString()).filter(value -> !value.isBlank()).toList());
        if (text.isBlank())
            text = AssistantView.tr(result.messageKey()) + (result.text().isBlank() ? "" : "\n" + result.text());
        List<String> speech = new ArrayList<>();
        SpeechConfig settings = SpeechConfig.INSTANCE.get();
        for (CardView card : cards) {
            SpeechContent content = card.speech();
            if (!content.main().getString().isBlank()) speech.add(content.main().getString());
            if (settings.questRequirements()) content.requirements().forEach(part -> speech.add(part.getString()));
            if (settings.questRewards()) content.rewards().forEach(part -> speech.add(part.getString()));
            if (settings.questDescription()) content.descriptions().forEach(part -> speech.add(part.getString()));
        }
        return new ClientMessage(text, result.silent() ? "" : String.join("\n", speech));
    }

    /**
     * Only text without buttons or a separate heading is a non-card message.
     */
    public Optional<String> plainText() {
        List<?> buttons = this.context.get(CardContext.RESULT_ACTIONS, List.class);
        if (!this.template.textOnly() || buttons != null && !buttons.isEmpty()
                || this.template.showTitle() && CardRenderer.enabled("show_card_title", this.context, List.of()))
            return Optional.empty();
        String text = CardRenderer.text(this.template.body(), this.template, this.context, List.of(), "\n");
        String summary = this.context.get("summary", String.class);
        if (summary != null && !LegacyText.plain(summary).isBlank()
                && !LegacyText.plain(summary).equals(LegacyText.plain(text))) return Optional.empty();
        return Optional.of(text);
    }

    public Element render(Document document) {
        Optional<String> plain = this.plainText();
        if (plain.isPresent()) return AssistantView.element(document, "div", "message_text", plain.get());
        Element result = AssistantView.element(document, "div", this.template.css(), "");
        if (this.template.showTitle() && CardRenderer.enabled("show_card_title", this.context, List.of()))
            result.appendChild(AssistantView.element(document, "div", "card_title", CardRenderer.value("{{card_title}}", this.context, List.of())));
        String main = this.context.get("summary", String.class);
        boolean summaryInBody = main != null && this.template.body().stream().anyMatch(node ->
                node.content() instanceof TextNode
                        && LegacyText.plain(main).equals(LegacyText.plain(
                        CardRenderer.text(List.of(node), this.template, this.context, List.of(), "\n"))));
        if (!summaryInBody) {
            String summary = this.context.get("summary", String.class);
            if (summary != null && !LegacyText.plain(summary).isBlank())
                result.appendChild(AssistantView.element(document, "div", "message_text", summary));
        }
        CardRenderer.render(document, result, this.template.body(), this.template, this.context, List.of());
        // A text template can still carry Java-defined buttons, as with the binding notice.
        if (this.template.textOnly()) CardRenderer.render(document, result,
                List.of(CardTemplate.Node.of(new ActionsNode("{{result_actions}}"))),
                this.template, this.context, List.of());
        return result;
    }
}
