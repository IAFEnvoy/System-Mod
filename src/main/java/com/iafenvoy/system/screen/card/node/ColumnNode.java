package com.iafenvoy.system.screen.card.node;

import com.mojang.serialization.MapCodec;

public final class ColumnNode extends ContainerNode {
    public static final MapCodec<ColumnNode> CODEC = ContainerNode.CODEC.xmap(ColumnNode::new, value -> value);

    public ColumnNode(ContainerNode source) {
        super(source);
    }

    @Override
    public MapCodec<ColumnNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_column";
    }
}
