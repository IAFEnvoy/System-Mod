package com.iafenvoy.system.screen.card.node;

import com.mojang.serialization.MapCodec;

public final class RowNode extends ContainerNode {
    public static final MapCodec<RowNode> CODEC = ContainerNode.CODEC.xmap(RowNode::new, value -> value);

    public RowNode(ContainerNode source) {
        super(source);
    }

    @Override
    public MapCodec<RowNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_row";
    }
}
