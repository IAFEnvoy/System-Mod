package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * Header and context nodes reuse the same immutable text data and formatting.
 */
public class TextNode implements CardNode {
    public static final MapCodec<TextNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("text", "").forGetter(TextNode::value),
            Codec.STRING.optionalFieldOf("translate", "").forGetter(TextNode::translate),
            Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(TextNode::arguments),
            Codec.BOOL.optionalFieldOf("inline", false).forGetter(TextNode::inline)
    ).apply(i, TextNode::new));
    private final String value;
    private final String translate;
    private final List<String> arguments;
    private final boolean inline;

    public TextNode(String value, String translate, List<String> arguments, boolean inline) {
        this.value = value;
        this.translate = translate;
        this.arguments = List.copyOf(arguments);
        this.inline = inline;
    }

    protected TextNode(TextNode source) {
        this(source.value, source.translate, source.arguments, source.inline);
    }

    public String value() {
        return this.value;
    }

    public String translate() {
        return this.translate;
    }

    public List<String> arguments() {
        return this.arguments;
    }

    public boolean inline() {
        return this.inline;
    }

    @Override
    public MapCodec<? extends TextNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_body";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        String value = this.format(view.context(), view.indices());
        return value.isBlank() ? null : view.element(this.inline ? "span" : "div", value);
    }

    @Override
    public String text(CardRenderer.Text view) {
        return this.format(view.context(), view.indices());
    }

    private String format(CardContext context, List<Integer> indices) {
        if (this.translate.isEmpty()) return CardRenderer.value(this.value, context, indices);
        Object[] arguments = this.arguments.stream().map(argument -> CardRenderer.value(argument, context, indices)).toArray();
        return LegacyText.scoped(Component.translatable(this.translate, arguments).getString());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TextNode that && this.getClass() == that.getClass() && this.value.equals(that.value)
                && this.translate.equals(that.translate) && this.arguments.equals(that.arguments) && this.inline == that.inline;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getClass(), this.value, this.translate, this.arguments, this.inline);
    }
}
