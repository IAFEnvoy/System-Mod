package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
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
public record DetailsNode(String summary, List<CardTemplate.Node> children, boolean open) implements CardNode {
    public static final MapCodec<DetailsNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("summary").forGetter(DetailsNode::summary),
            Codec.lazyInitialized(() -> CardTemplate.Node.CODEC).listOf().fieldOf("children").forGetter(DetailsNode::children),
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
        Element content = AssistantView.element(view.document(), "div", "card_details_body", "");
        CardRenderer.render(view.document(), content, this.children, view.template(), view.context(), view.indices());
        if (content.children.isEmpty()) return null;
        String label = CardRenderer.value(this.summary, view.context(), view.indices());
        Element toggle = AssistantView.element(view.document(), "button", "card_details_toggle", "");
        toggle.setAttribute("type", "button");
        Element arrow = AssistantView.element(view.document(), "span", "card_details_arrow", "");
        arrow.setAttribute("aria-hidden", "true");
        toggle.appendChild(arrow);
        toggle.appendChild(AssistantView.element(view.document(), "span", "card_control_label", label));
        this.expanded(toggle, arrow, content, this.open);
        toggle.addEventListener("click", event -> {
            if (!view.interactive()) return;
            this.expanded(toggle, arrow, content, !"true".equals(toggle.getAttribute("aria-expanded")));
            // Reflow the revealed subtree before painting or querying the next pointer position.
            content.invalidateStyle();
            AssistantPages.commit(view.document());
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
