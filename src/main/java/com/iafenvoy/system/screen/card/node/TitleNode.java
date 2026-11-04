package com.iafenvoy.system.screen.card.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Heading with an h1-h4 level. The first top-level level-1 node owns the card heading and is drawn above the
 * summary by the presentation; every other level stays inline at its position with a smaller style.
 */
public final class TitleNode extends TextNode {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 4;
    public static final MapCodec<TitleNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("text", "").forGetter(TextNode::value),
            Codec.STRING.optionalFieldOf("translate", "").forGetter(TextNode::translate),
            Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(TextNode::arguments),
            Codec.BOOL.optionalFieldOf("inline", false).forGetter(TextNode::inline),
            Codec.INT.optionalFieldOf("level", MIN_LEVEL).forGetter(TitleNode::level)
    ).apply(i, TitleNode::new));
    private final int level;

    public TitleNode(String value, String translate, List<String> arguments, boolean inline, int level) {
        super(value, translate, arguments, inline);
        this.level = Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
    }

    public TitleNode(TextNode source, int level) {
        this(source.value(), source.translate(), source.arguments(), source.inline(), level);
    }

    public int level() {
        return this.level;
    }

    public boolean cardHeading() {
        return this.level == MIN_LEVEL;
    }

    @Override
    public MapCodec<TitleNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return this.cardHeading() ? "card_title" : "card_title_" + this.level;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TitleNode that && super.equals(that) && this.level == that.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.level);
    }
}
