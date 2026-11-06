package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.card.CardAction;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardPagination;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.stream.IntStream;

public record ActionsNode(String actions, int pageSize) implements CardNode {
    public static final MapCodec<ActionsNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("actions", "{{result_actions}}").forGetter(ActionsNode::actions),
            Codec.INT.lenientOptionalFieldOf("page_size", 0).forGetter(ActionsNode::pageSize)
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
        List<Integer> indices = indices(view, actions);
        if (indices.isEmpty()) return null;
        if (this.pageSize > 0 && (CardRenderer.enabled("notification", context, List.of()) || CardRenderer.enabled("full", context, List.of())))
            return CardPagination.render(view, "actions:" + CardRenderer.binding(this.actions, view.indices()), indices,
                    this.pageSize, false, (parent, page) -> this.renderActions(view, parent, actions, page));
        Element element = view.element("div", "");
        this.renderActions(view, element, actions, indices);
        return element;
    }

    static List<Integer> indices(CardRenderer.Render view, List<?> actions) {
        CardContext context = view.context();
        boolean notification = CardRenderer.enabled("notification", context, List.of());
        return IntStream.range(0, actions.size()).filter(index -> actions.get(index) instanceof CardButton button
                && (!notification || button.placement() == CardButton.Placement.BODY)).boxed().toList();
    }

    private void renderActions(CardRenderer.Render view, Element element, List<?> actions, List<Integer> indices) {
        CardContext context = view.context();
        CardTemplate.Actions layout = view.template().actions();
        element.setClassName(view.css() + (layout.containerCss().isBlank() ? "" : " " + layout.containerCss()));
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
                bind(view, button, action, index);
                element.appendChild(button);
            }
        }
    }

    static void bind(CardRenderer.Render view, Element element, CardButton action, int index) {
        boolean server = action.action() instanceof CardAction.Server;
        if (server) {
            element.setAttribute("data-requires-idle", "true");
            element.setDisabled(AssistantClient.state().busy());
        }
        Runnable callback = view.context().callbacks().get("actions." + index + ".click");
        ButtonNode.bind(element, callback == null ? null : () -> {
            if (view.interactive() && (!server || !AssistantClient.state().busy())) callback.run();
        });
    }
}
