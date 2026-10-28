package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;

public record ButtonNode(String value, String callback) implements CardNode {
    public static final MapCodec<ButtonNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(ButtonNode::value),
            Codec.STRING.fieldOf("callback").forGetter(ButtonNode::callback)
    ).apply(i, ButtonNode::new));

    @Override
    public MapCodec<ButtonNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_action";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        // HUD controls remain code-owned; simple mode exposes the label without an interactive button.
        if (!CardRenderer.enabled("full", view.context(), List.of()) && !CardRenderer.enabled("simple", view.context(), List.of()))
            return null;
        String label = CardRenderer.value(this.value, view.context(), view.indices());
        if (label.isBlank()) return null;
        boolean full = CardRenderer.enabled("full", view.context(), List.of());
        Element element = view.element(full ? "button" : "div", label);
        if (full) bind(element, view.context().callbacks().get(CardRenderer.binding(this.callback, view.indices())));
        return element;
    }

    static void bind(Element button, Runnable callback) {
        if (callback == null) button.setDisabled(true);
        else button.addEventListener("click", event -> callback.run());
    }
}
