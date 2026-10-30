package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.card.CardAction;
import com.iafenvoy.system.card.CardButton;
import com.iafenvoy.system.card.CardContext;
import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardPageState;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.IntStream;

public record ActionsNode(String actions, int notificationPageSize) implements CardNode {
    public static final MapCodec<ActionsNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("actions", "{{result_actions}}").forGetter(ActionsNode::actions),
            Codec.INT.optionalFieldOf("notification_page_size", 0).forGetter(ActionsNode::notificationPageSize)
    ).apply(i, ActionsNode::new));

    public ActionsNode(String actions) {
        this(actions, 0);
    }

    @Override
    public MapCodec<ActionsNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_actions";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        CardContext context = view.context();
        List<?> actions = context.get(CardRenderer.binding(this.actions, view.indices()), List.class);
        if (actions == null || actions.isEmpty()) return null;
        // AUI upgrades raw tags on insertion; callbacks must retain that live instance, not its detached precursor.
        Element element = Element.init(view.element("div", ""));
        boolean notification = CardRenderer.enabled("notification", context, List.of());
        List<Integer> indices = IntStream.range(0, actions.size()).filter(index -> actions.get(index) instanceof CardButton button
                && (!notification || button.placement() == CardButton.Placement.BODY)).boxed().toList();
        if (indices.isEmpty()) return null;
        if (notification && this.notificationPageSize > 0) {
            this.renderPage(view, element, actions, indices);
            return element;
        }
        this.renderActions(view, element, actions, indices);
        return element;
    }

    private void renderActions(CardRenderer.Render view, Element element, List<?> actions, List<Integer> indices) {
        CardContext context = view.context();
        CardTemplate.Actions layout = view.template().actions();
        if (!layout.containerCss().isBlank()) element.setClassName(view.css() + " " + layout.containerCss());
        for (int index : indices) {
            CardButton action = (CardButton) actions.get(index);
            String id = "actions." + index + ".";
            String label = view.template().label(context, index);
            if (CardRenderer.enabled("simple", context, List.of())) {
                Number number = context.get(id + "number", Number.class);
                element.appendChild(AssistantView.element(view.document(), "div", "card_body",
                        (layout.numbered() && number != null ? number + ". " : "") + label));
            } else {
                Element button = AssistantView.element(view.document(), "button", layout.buttonCss(), label);
                if (action.action() instanceof CardAction.Server) {
                    button.setAttribute("data-requires-idle", "true");
                    button.setDisabled(AssistantClient.state().busy());
                }
                ButtonNode.bind(button, context.callbacks().get(id + "click"));
                element.appendChild(button);
            }
        }
    }

    private void renderPage(CardRenderer.Render view, Element element, List<?> actions, List<Integer> indices) {
        CardPageState pages = view.context().get("local_pages", CardPageState.class);
        if (pages == null) pages = new CardPageState();
        String key = CardRenderer.binding(this.actions, view.indices());
        int total = (indices.size() - 1) / this.notificationPageSize + 1;
        int page = Math.clamp(pages.page(key), 0, total - 1);
        pages.page(key, page);
        element.setInnerHTML("");
        Element rows = AssistantView.element(view.document(), "div", "", "");
        int from = page * this.notificationPageSize;
        this.renderActions(view, rows, actions, indices.subList(from, Math.min(from + this.notificationPageSize, indices.size())));
        element.appendChild(rows);
        if (total == 1) return;
        // These controls only rebuild this node; they never dispatch card actions or replace a notification.
        Element controls = AssistantView.element(view.document(), "div", "card_pagination", "");
        this.pageButton(view, element, controls, actions, indices, pages, key, page - 1, page > 0, "system.ui.card_previous_page");
        controls.appendChild(AssistantView.element(view.document(), "span", "card_page_label",
                Component.translatable("system.ui.card_page", page + 1, total).getString()));
        this.pageButton(view, element, controls, actions, indices, pages, key, page + 1, page + 1 < total, "system.ui.card_next_page");
        element.appendChild(controls);
    }

    private void pageButton(CardRenderer.Render view, Element root, Element controls, List<?> actions, List<Integer> indices,
                            CardPageState pages, String key, int page, boolean enabled, String label) {
        Element button = AssistantView.element(view.document(), "button", "card_action", AssistantView.tr(label));
        button.setDisabled(!enabled);
        button.addEventListener("click", event -> {
            if (!enabled || !view.interactive() || !root.isConnected()) return;
            pages.page(key, page);
            this.renderPage(view, root, actions, indices);
            Element scroll = view.document().getElementById("notification_scroll");
            if (scroll != null) scroll.scrollTop = scroll.targetScrollTop = 0;
            root.getRenderer().invalidateLayoutSubtree();
            AssistantPages.commit(view.document());
        });
        controls.appendChild(button);
    }
}
