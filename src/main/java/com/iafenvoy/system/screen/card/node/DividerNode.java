package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;

public record DividerNode() implements CardNode {
    public static final MapCodec<DividerNode> CODEC = MapCodec.unit(new DividerNode());

    @Override
    public MapCodec<DividerNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_divider";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        return view.element("div", "");
    }
}
