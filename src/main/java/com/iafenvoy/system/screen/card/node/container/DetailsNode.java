package com.iafenvoy.system.screen.card.node.container;

import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.card.ChildrenNode;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.stream.Stream;

/**
 * Disclosure state belongs to this rendered instance; toggling never changes card data or sends a request.
 */
public record DetailsNode(String summary, List<CardTemplate.Node> children,
                          boolean open) implements ChildrenNode, TextProjectingNode {
    public static final MapCodec<DetailsNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("summary").forGetter(DetailsNode::summary),
            CardTemplate.NODES.fieldOf("children").forGetter(DetailsNode::children),
            Codec.BOOL.optionalFieldOf("open", false).forGetter(DetailsNode::open)
    ).apply(i, DetailsNode::new));

    public DetailsNode {
        children = List.copyOf(children);
    }

    @Override
    public MapCodec<DetailsNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_details";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        Element content = Element.init(AssistantView.element(view.document(), "div", "card_details_body", ""));
        this.renderChildren(view, content);
        if (content.children.isEmpty()) return null;
        String label = CardRenderer.value(this.summary, view.context(), view.indices());
        Element toggle = Element.init(AssistantView.element(view.document(), "button", "card_details_toggle", ""));
        toggle.setAttribute("type", "button");
        Element arrow = Element.init(AssistantView.element(view.document(), "span", "card_details_arrow", ""));
        arrow.setAttribute("aria-hidden", "true");
        toggle.appendChild(arrow);
        toggle.appendChild(AssistantView.element(view.document(), "span", "card_control_label", label));
        this.expanded(toggle, arrow, content, view.ui().expanded(view.stateKey(), this.open));
        toggle.addEventListener("click", unused1 -> {
            if (!view.interactive()) return;
            boolean expanded = !"true".equals(toggle.getAttribute("aria-expanded"));
            view.ui().setExpanded(view.stateKey(), expanded);
            this.expanded(toggle, arrow, content, expanded);
            // Keep the live native nodes and let AUI reflow the revealed subtree before the next paint.
            content.getLayout().invalidateLayoutSubtree();
        });
        Element element = view.element("div", "");
        element.appendChild(toggle);
        element.appendChild(content);
        return element;
    }

    private void expanded(Element toggle, Element arrow, Element content, boolean expanded) {
        toggle.setAttribute("aria-expanded", Boolean.toString(expanded));
        arrow.setTextContent(expanded ? "▾" : "▸");
        content.setInlineStyleProperty("display", expanded ? "block" : "none");
    }

    @Override
    public String text(CardRenderer.Text view) {
        // Plain-text exports retain the contents; speech continues to use the card's separate SpeechContent.
        return String.join(view.separator(), Stream.of(CardRenderer.value(this.summary, view.context(), view.indices()),
                        CardRenderer.text(this.children, view.template(), view.context(), view.indices(), view.separator()))
                .filter(value -> !value.isBlank()).toList());
    }
}
