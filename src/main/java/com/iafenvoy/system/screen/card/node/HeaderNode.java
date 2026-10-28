package com.iafenvoy.system.screen.card.node;

import com.mojang.serialization.MapCodec;

public final class HeaderNode extends TextNode {
    public static final MapCodec<HeaderNode> CODEC = TextNode.CODEC.xmap(HeaderNode::new, value -> value);

    public HeaderNode(TextNode source) {
        super(source);
    }

    @Override
    public MapCodec<HeaderNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_title";
    }
}
