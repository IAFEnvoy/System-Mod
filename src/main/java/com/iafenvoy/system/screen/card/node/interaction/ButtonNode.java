package com.iafenvoy.system.screen.card.node.interaction;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.card.CardAction;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.client.CardController;
import com.iafenvoy.system.screen.card.*;
import com.iafenvoy.system.screen.card.node.layout.FlexNode;
import com.iafenvoy.system.screen.card.node.media.ImageNode;
import com.iafenvoy.system.screen.card.node.text.TextContent;
import com.iafenvoy.system.screen.card.node.text.TextNode;
import com.iafenvoy.system.screen.gui.AssistantPageScreen;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * KOOK-style link / return-val actions, plus local callbacks for client UI operations.
 */
public record ButtonNode(String value, String click, String text, String label, String icon, String selected,
                         String disabled, String binding, String behavior, List<CardTemplate.Node> children)
        implements ChildrenNode, TextProjectingNode {
    public static final MapCodec<ButtonNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("value", "").forGetter(ButtonNode::value),
            Codec.STRING.lenientOptionalFieldOf("click", "").forGetter(ButtonNode::click),
            Codec.STRING.lenientOptionalFieldOf("text", "").forGetter(ButtonNode::text),
            Codec.STRING.lenientOptionalFieldOf("label", "").forGetter(ButtonNode::label),
            Codec.STRING.lenientOptionalFieldOf("icon", "").forGetter(ButtonNode::icon),
            Codec.STRING.lenientOptionalFieldOf("selected", "").forGetter(ButtonNode::selected),
            Codec.STRING.lenientOptionalFieldOf("disabled", "").forGetter(ButtonNode::disabled),
            Codec.STRING.lenientOptionalFieldOf("binding", "").forGetter(ButtonNode::binding),
            Codec.STRING.lenientOptionalFieldOf("behavior", "replace").forGetter(ButtonNode::behavior),
            CardTemplate.NODES.lenientOptionalFieldOf("children", List.of()).forGetter(ButtonNode::children)
    ).apply(i, ButtonNode::new));

    public ButtonNode {
        children = List.copyOf(children);
    }

    @Override
    public MapCodec<ButtonNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_action";
    }

    public static CardTemplate.Node actions() {
        ButtonNode button = new ButtonNode("{{actions.*.value}}", "{{actions.*.click_type}}", "", "", "", "",
                "{{actions.*.disabled}}", "", "{{actions.*.behavior}}", List.of(
                new CardTemplate.Node(new TextNode(TextContent.of("{{actions.*.number}}. ")),
                        Optional.empty(), "show_action_numbers", "", "", ""),
                CardTemplate.Node.of(new TextNode(TextContent.of("{{actions.*.label}}")))));
        return CardTemplate.Node.of(new FlexNode("row", true, "{{actions}}", "{{actions.*.visible}}", 0,
                List.of(CardTemplate.Node.of(button)), Optional.empty()));
    }

    @Override
    public Element render(CardRenderer.Render view) {
        String type = CardRenderer.rawValue(this.click, view.context(), view.indices());
        String value = CardRenderer.rawValue(this.value, view.context(), view.indices());
        String label = CardRenderer.value(this.text, view.context(), view.indices());
        boolean simple = CardRenderer.enabled("simple", view.context(), List.of());
        Element element = Element.init(view.element(simple ? "div" : "button", ""));
        if (!simple) element.setAttribute("type", "button");
        if (!this.icon.isBlank()) {
            Element image = ImageNode.render(view, CardRenderer.rawValue(this.icon, view.context(), view.indices()), "card_button_icon", 16, 16);
            if (image != null) element.appendChild(image);
        }
        if (!label.isBlank())
            element.appendChild(AssistantView.element(view.document(), "span", "card_control_label", label));
        this.renderChildren(view, element);
        String accessible = CardRenderer.value(this.label, view.context(), view.indices());
        if (!accessible.isBlank()) {
            element.setAttribute("aria-label", accessible);
            element.setAttribute("title", accessible);
        }
        if (!this.selected.isBlank())
            element.setAttribute("aria-pressed", Boolean.toString(CardRenderer.enabled(this.selected, view.context(), view.indices())));
        boolean disabled = !this.disabled.isBlank() && CardRenderer.enabled(this.disabled, view.context(), view.indices());
        Runnable callback = null;
        boolean server = "return-val".equals(type);
        if ("local".equals(type)) {
            callback = view.context().callbacks().get(CardRenderer.binding(value, view.indices()));
            if (callback == null)
                callback = view.context().callbacks().get(CardRenderer.binding(this.value, view.indices()));
        } else if (server && !value.isBlank()) {
            CardButton.Behavior behavior;
            try {
                behavior = CardButton.Behavior.valueOf(CardRenderer.rawValue(this.behavior, view.context(), view.indices()).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                behavior = CardButton.Behavior.REPLACE;
            }
            CardButton action = new CardButton(view.path(), "", new CardAction.Server(value), CardButton.Placement.BODY, behavior);
            callback = () -> CardController.activate(view.reference(), action, CardRenderer.enabled("notification", view.context(), List.of()));
        } else if ("link".equals(type) && !value.isBlank()) callback = () -> AssistantPageScreen.openLink(value);
        if (!simple) {
            bind(view, element, disabled ? null : callback, server);
            CardButtonBinding state = view.context().get(CardRenderer.binding(this.binding, view.indices()), CardButtonBinding.class);
            if (state != null) state.bind(element);
        }
        return element;
    }

    public static void bind(CardRenderer.Render view, Element element, Runnable callback, boolean server) {
        element.setDisabled(callback == null || server && AssistantClient.state().busy());
        if (server) {
            element.setAttribute("data-requires-idle", "true");
            element.setAttribute("data-card-disabled", Boolean.toString(callback == null));
        }
        if (callback != null) element.addEventListener("click", event -> {
            event.stopPropagation();
            event.preventDefault();
            if (view.interactive() && element.isConnected() && !element.isDisabled()
                    && (!server || !AssistantClient.state().busy())) callback.run();
        });
    }

    @Override
    public String text(CardRenderer.Text view) {
        String label = CardRenderer.value(this.text, view.context(), view.indices());
        String contents = this.textChildren(view, view.separator());
        return label.isBlank() ? contents : contents.isBlank() ? label : label + view.separator() + contents;
    }
}
