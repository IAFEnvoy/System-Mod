package com.iafenvoy.system.screen.card.node.interaction;

import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.iafenvoy.system.screen.gui.AssistantPageScreen;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

/**
 * URLs remain literal data and open through vanilla link preferences and confirmation, never AUI navigation.
 */
public record LinkNode(String value, String url) implements TextProjectingNode {
    public static final MapCodec<LinkNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("text", "").forGetter(LinkNode::value),
            Codec.STRING.fieldOf("url").forGetter(LinkNode::url)
    ).apply(i, LinkNode::new));

    @Override
    public MapCodec<LinkNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_link";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        String url = CardRenderer.rawValue(this.url, view.context(), view.indices());
        if (url.isBlank()) return null;
        String label = this.value.isBlank() ? url : CardRenderer.value(this.value, view.context(), view.indices());
        // A block label is one flex item; the button itself never paints a second text run.
        Element element = Element.init(view.element("button", ""));
        Element text = AssistantView.element(view.document(), "span", "card_control_label", "");
        if (this.value.isBlank()) text.setTextContent(url);
        else AssistantView.text(text, label);
        element.appendChild(text);
        element.setAttribute("type", "button");
        element.setAttribute("role", "link");
        element.setAttribute("title", url);
        ButtonNode.bind(view, element, () -> AssistantPageScreen.openLink(url), false);
        return element;
    }

    @Override
    public String text(CardRenderer.Text view) {
        String url = CardRenderer.rawValue(this.url, view.context(), view.indices());
        if (url.isBlank()) return "";
        String label = CardRenderer.value(this.value, view.context(), view.indices());
        return label.isBlank() || label.equals(url) ? url : label + " (" + url + ")";
    }
}
