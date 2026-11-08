package com.iafenvoy.system.screen.card.node.text;

import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.PlainMessageNode;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;

public record TextNode(TextContent content) implements PlainMessageNode {
    public static final MapCodec<TextNode> CODEC = TextContent.CODEC.xmap(TextNode::new, TextNode::content);

    @Override
    public MapCodec<TextNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_body";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        String text = this.content.resolve(view.context(), view.indices());
        return text.isBlank() ? null : view.element(this.content.inline() ? "span" : "div", text);
    }

    @Override
    public String text(CardRenderer.Text view) {
        return this.content.resolve(view.context(), view.indices());
    }
}
