package com.iafenvoy.system.screen.card.node.text;

import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.iafenvoy.system.screen.gui.AssistantMarkdown;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;

/**
 * Markdown is opt-in; plain text and FTB formatting remain owned by TextNode.
 */
public record MarkdownNode(String value) implements TextProjectingNode {
    public static final MapCodec<MarkdownNode> CODEC = Codec.STRING.lenientOptionalFieldOf("text", "{{summary}}")
            .xmap(MarkdownNode::new, MarkdownNode::value);

    @Override
    public MapCodec<MarkdownNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_markdown";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        String source = CardRenderer.rawValue(this.value, view.context(), view.indices());
        return source.isBlank() ? null : AssistantMarkdown.render(view, source);
    }

    @Override
    public String text(CardRenderer.Text view) {
        return AssistantMarkdown.text(CardRenderer.rawValue(this.value, view.context(), view.indices()));
    }
}
