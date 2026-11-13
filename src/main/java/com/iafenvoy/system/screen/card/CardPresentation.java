package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.data.card.*;
import com.iafenvoy.system.data.card.client.CardController;
import com.iafenvoy.system.data.card.client.CardFormatting;
import com.iafenvoy.system.data.card.client.ClientCards;
import com.iafenvoy.system.module.client.ClientMessage;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.card.node.interaction.ButtonNode;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * The single client construction path resolves local text, templates and trusted button callbacks.
 */
public record CardPresentation(CardTemplate template, CardContext context, int hoistedTitle, CardUiState ui,
                               CardReference reference) {
    public static CardPresentation of(CardUpdate original, String messageKey, CardContext.Mode mode, int actionOffset) {
        return of(original, messageKey, mode, actionOffset, new CardUiState());
    }

    public static CardPresentation of(CardUpdate original, String messageKey, CardContext.Mode mode, int actionOffset, CardUiState pages) {
        CardUpdate update = ClientCards.latest(original);
        CardView view = ClientCards.view(update);
        return prepare(update.ref(), view, messageKey, mode, actionOffset, pages, ClientCards.active(update.ref()));
    }

    /**
     * Local workspace cards reuse templates and callbacks without entering message history or the server card cache.
     */
    public static CardPresentation local(CardView view) {
        return prepare(new CardReference(UUID.randomUUID(), 0), view, "", CardContext.Mode.FULL, 0, new CardUiState(), false);
    }

    private static CardPresentation prepare(CardReference reference, CardView view, String messageKey,
                                             CardContext.Mode mode, int actionOffset, CardUiState pages, boolean active) {
        CardTemplate template = CardTemplates.get(view.template());
        if (template == null) template = CardTemplate.fallback();
        CardContext.Builder builder = CardContext.builder();
        view.context().callbacks().forEach(builder::callback);
        view.context().parameters().forEach(builder::put);
        view.context().parameters().forEach((key, value) -> {
            if (value instanceof Component) builder.put(key, CardFormatting.FORMATTED.value(value));
        });
        view.context().states().forEach((key, value) -> {
            Object parameter = view.context().parameters().get(key);
            if (!(parameter instanceof Component)) builder.enable(key, value);
        });
        builder.put("summary", CardFormatting.FORMATTED.text(view.summary())).put("card_title", view.titleKey().isBlank() ? "" : AssistantView.tr(view.titleKey()))
                .enable("show_card_title", !view.titleKey().isBlank() && !view.titleKey().equals(messageKey))
                .enable("text_items", mode == CardContext.Mode.TEXT)
                .enable("full", mode == CardContext.Mode.FULL).enable("simple", mode == CardContext.Mode.SIMPLE)
                .enable("hud", mode == CardContext.Mode.HUD)
                .enable("notification", mode == CardContext.Mode.NOTIFICATION).put(CardContext.RESULT_ACTIONS, view.buttons()).put("actions", view.buttons().size());
        builder.enable("show_action_numbers", mode == CardContext.Mode.SIMPLE && template.actions().numbered());
        for (int i = 0; i < view.buttons().size(); i++) {
            CardButton button = view.buttons().get(i);
            String prefix = "actions." + i + ".";
            if (view.context().get(prefix + "label", Object.class) == null)
                builder.put(prefix + "label", AssistantView.tr(button.labelKey()));
            builder.put(prefix + "number", actionOffset + i + 1)
                    .enable(prefix + "visible", mode != CardContext.Mode.NOTIFICATION || button.placement() == CardButton.Placement.BODY)
                    .put(prefix + "behavior", button.behavior().name().toLowerCase(Locale.ROOT));
            if (button.action() instanceof CardAction.Server(String value1))
                builder.put(prefix + "click_type", "return-val").put(prefix + "value", value1);
            else if (button.action() instanceof CardAction.Url(String value))
                builder.put(prefix + "click_type", "link").put(prefix + "value", value);
            else builder.put(prefix + "click_type", "local").put(prefix + "value", prefix + "click");
            builder.enable(prefix + "disabled", !active);
            if (active) builder.callback(prefix + "click", () -> {
                if (mode != CardContext.Mode.NOTIFICATION || AssistantMessages.canActivate(reference))
                    CardController.activate(reference, button, mode == CardContext.Mode.NOTIFICATION);
            });
        }
        // Resolve headings after components, summary and mode flags are ready, just like the card body.
        CardContext context = builder.build();
        int hoisted = hoistedTitle(template, context);
        if (hoisted >= 0) {
            String title = CardRenderer.text(List.of(template.body().get(hoisted)), template, context, List.of(), "");
            context = builder.put("card_title", title).enable("show_card_title", true).build();
        }
        return new CardPresentation(template, context, hoisted, pages, reference);
    }

    /**
     * Only a visible top-level h1 can become the card heading; deeper levels keep their place in the body.
     */
    private static int hoistedTitle(CardTemplate template, CardContext context) {
        List<CardTemplate.Node> body = template.body();
        for (int index = 0; index < body.size(); index++) {
            CardTemplate.Node node = body.get(index);
            if (!(node.content() instanceof HeadingNode heading) || !node.repeat().isEmpty())
                continue;
            if (CardRenderer.enabled(node.when(), context, List.of()) && (node.unless().isEmpty() || !CardRenderer.enabled(node.unless(), context, List.of()))
                    && heading.cardHeading(new CardRenderer.Text(template, context, List.of(), "")).isPresent())
                return index;
        }
        return -1;
    }

    public static String format(CardUpdate update) {
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
                node.content() instanceof PlainMessageNode
                        && LegacyText.plain(main).equals(LegacyText.plain(
                        CardRenderer.text(List.of(node), this.template, this.context, List.of(), "\n"))));
        // A template-authored heading already names the card, so the summary would repeat it on screen. The summary
        // stays in the card data, which keeps the chat line and the speech output intact.
        if (this.hoistedTitle < 0 && !this.template.hideSummary() && !summaryInBody) {
            String summary = this.context.get("summary", String.class);
            if (summary != null && !LegacyText.plain(summary).isBlank())
                result.appendChild(AssistantView.element(document, "div", "message_text", summary));
        }
        CardRenderer.Render view = new CardRenderer.Render(document, "", this.template, this.context, List.of(), this.ui, this.reference, this.reference.id().toString(), Map.of());
        CardRenderer.render(view, result, this.body());
        // A text template can still carry Java-defined buttons, as with the binding notice.
        if (this.template.textOnly()) CardRenderer.render(view, result, List.of(ButtonNode.actions()));
        return result;
    }

    /**
     * The hoisted heading is already drawn above the summary, so the body skips exactly that node.
     */
    private List<CardTemplate.Node> body() {
        List<CardTemplate.Node> body = this.template.body();
        if (this.hoistedTitle < 0 || this.hoistedTitle >= body.size()) return body;
        List<CardTemplate.Node> visible = new ArrayList<>(body);
        visible.remove(this.hoistedTitle);
        return visible;
    }
}
