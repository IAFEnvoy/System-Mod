package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.card.CardAction;
import com.iafenvoy.system.card.CardButton;
import com.iafenvoy.system.card.CardContext;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;

import java.util.List;

public record ActionsNode(String actions) implements CardNode {
    public static final MapCodec<ActionsNode> CODEC = Codec.STRING.optionalFieldOf("actions", "{{result_actions}}")
            .xmap(ActionsNode::new, ActionsNode::actions);

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
        Element element = view.element("div", "");
        CardTemplate.Actions layout = view.template().actions();
        if (!layout.containerCss().isBlank()) element.setClassName(view.css() + " " + layout.containerCss());
        for (int index = 0; index < actions.size(); index++) {
            if (!(actions.get(index) instanceof CardButton action)) continue;
            if (CardRenderer.enabled("notification", context, List.of()) && action.placement() != CardButton.Placement.BODY)
                continue;
            String id = "actions." + index + ".";
            String label = view.template().label(context, index);
            if (CardRenderer.enabled("simple", context, List.of())) {
                Number number = context.get(id + "number", Number.class);
                element.appendChild(AssistantView.element(view.document(), "div", "card_body",
                        (layout.numbered() && number != null ? number + ". " : "") + label));
            } else {
                Element button = AssistantView.element(view.document(), "button", layout.buttonCss(), label);
                if (action.action() instanceof CardAction.Server)
                    button.setAttribute("data-requires-idle", "true");
                ButtonNode.bind(button, context.callbacks().get(id + "click"));
                element.appendChild(button);
            }
        }
        return element.children.isEmpty() ? null : element;
    }
}
