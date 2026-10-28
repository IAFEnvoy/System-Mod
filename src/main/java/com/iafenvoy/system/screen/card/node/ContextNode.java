package com.iafenvoy.system.screen.card.node;

import com.mojang.serialization.MapCodec;

public final class ContextNode extends TextNode {
    public static final MapCodec<ContextNode> CODEC = TextNode.CODEC.xmap(ContextNode::new, value -> value);

    public ContextNode(TextNode source) {
        super(source);
    }

    @Override
    public MapCodec<ContextNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_context";
    }
}
