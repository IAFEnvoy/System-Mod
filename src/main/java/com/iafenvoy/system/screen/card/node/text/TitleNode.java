package com.iafenvoy.system.screen.card.node.text;

import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.HeadingNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.Optional;

/**
 * A heading shares text data, but never inherits plain-message eligibility.
 */
public record TitleNode(TextContent content, int level) implements HeadingNode {
    public static final MapCodec<TitleNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TextContent.CODEC.forGetter(TitleNode::content),
            Codec.INT.lenientOptionalFieldOf("level", 1).forGetter(TitleNode::level)
    ).apply(i, TitleNode::new));

    public TitleNode {
        level = Math.clamp(level, 1, 4);
    }

    @Override
    public MapCodec<TitleNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return this.level == 1 ? "card_title" : "card_title_" + this.level;
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

    @Override
    public Optional<String> cardHeading(CardRenderer.Text view) {
        String text = this.text(view);
        return this.level == 1 && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }
}
