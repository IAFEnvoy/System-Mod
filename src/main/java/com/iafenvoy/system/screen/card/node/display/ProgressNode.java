package com.iafenvoy.system.screen.card.node.display;

import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.CardProgressElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;

/**
 * A percentage from the typed context controls a CSS bar; no layout measurements or text parsing are needed.
 */
public record ProgressNode(String value) implements CardNode {
    public static final MapCodec<ProgressNode> CODEC = Codec.STRING.fieldOf("value").xmap(ProgressNode::new, ProgressNode::value);

    @Override
    public MapCodec<ProgressNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_progress";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        Number value = view.context().get(CardRenderer.binding(this.value, view.indices()), Number.class);
        if (value == null || !Double.isFinite(value.doubleValue())) return null;
        double amount = Math.clamp(value.doubleValue(), 0, 100);
        String progress = Double.toString(amount);
        Element track = view.element("div", "");
        track.setAttribute("role", "progressbar");
        track.setAttribute("aria-valuemin", "0");
        track.setAttribute("aria-valuemax", "100");
        track.setAttribute("aria-valuenow", progress);
        track.appendChild(new CardProgressElement(view.document(),
                view.ui().progress(CardRenderer.binding(this.value, view.indices()), amount)));
        return track;
    }
}
