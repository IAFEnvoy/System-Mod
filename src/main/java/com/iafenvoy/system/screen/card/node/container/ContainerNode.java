package com.iafenvoy.system.screen.card.node.container;

import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.card.ChildrenNode;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.Optional;

public record ContainerNode(List<CardTemplate.Node> children,
                            Optional<String> textSeparator) implements ChildrenNode, TextProjectingNode {
    public static final MapCodec<ContainerNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CardTemplate.NODES.fieldOf("children").forGetter(ContainerNode::children),
            Codec.STRING.lenientOptionalFieldOf("text_separator").forGetter(ContainerNode::textSeparator)
    ).apply(i, ContainerNode::new));

    public ContainerNode {
        children = List.copyOf(children);
    }

    @Override
    public MapCodec<ContainerNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_container";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        Element element = view.element("div", "");
        this.renderChildren(view, element);
        return element.children.isEmpty() ? null : element;
    }

    @Override
    public String text(CardRenderer.Text view) {
        return this.textChildren(view, this.textSeparator.orElse(view.separator()));
    }
}
