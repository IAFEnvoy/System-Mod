package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Children use the same context; only their order and optional text separator belong to this node.
 */
public class ContainerNode implements CardNode {
    public static final MapCodec<ContainerNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.lazyInitialized(() -> CardTemplate.Node.CODEC).listOf().fieldOf("children").forGetter(ContainerNode::children),
            Codec.STRING.optionalFieldOf("text_separator").forGetter(ContainerNode::textSeparator)
    ).apply(i, ContainerNode::new));
    private final List<CardTemplate.Node> children;
    private final Optional<String> textSeparator;

    public ContainerNode(List<CardTemplate.Node> children, Optional<String> textSeparator) {
        this.children = List.copyOf(children);
        this.textSeparator = textSeparator;
    }

    protected ContainerNode(ContainerNode source) {
        this(source.children, source.textSeparator);
    }

    public List<CardTemplate.Node> children() {
        return this.children;
    }

    public Optional<String> textSeparator() {
        return this.textSeparator;
    }

    @Override
    public MapCodec<? extends ContainerNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_container";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        Element element = view.element("div", "");
        CardRenderer.render(view.document(), element, this.children, view.template(), view.context(), view.indices());
        return element.children.isEmpty() ? null : element;
    }

    @Override
    public String text(CardRenderer.Text view) {
        return CardRenderer.text(this.children, view.template(), view.context(), view.indices(), this.textSeparator.orElse(view.separator()));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ContainerNode that && this.getClass() == that.getClass()
                && this.children.equals(that.children) && this.textSeparator.equals(that.textSeparator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getClass(), this.children, this.textSeparator);
    }
}
