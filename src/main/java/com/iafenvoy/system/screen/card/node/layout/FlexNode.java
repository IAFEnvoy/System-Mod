package com.iafenvoy.system.screen.card.node.layout;

import com.iafenvoy.system.screen.card.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.Optional;

/**
 * Static children and repeated entries share one flex layout and the common page slicer.
 */
public record FlexNode(String direction, boolean wrap, String source, String filter, int pageSize,
                       List<CardTemplate.Node> children,
                       Optional<String> textSeparator) implements CollectionNode, TextProjectingNode {
    public static final MapCodec<FlexNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("direction", "row").forGetter(FlexNode::direction),
            Codec.BOOL.lenientOptionalFieldOf("wrap", true).forGetter(FlexNode::wrap),
            Codec.STRING.lenientOptionalFieldOf("source", "").forGetter(FlexNode::source),
            Codec.STRING.lenientOptionalFieldOf("filter", "").forGetter(FlexNode::filter),
            Codec.INT.lenientOptionalFieldOf("page_size", 0).forGetter(FlexNode::pageSize),
            CardTemplate.NODES.fieldOf("children").forGetter(FlexNode::children),
            Codec.STRING.lenientOptionalFieldOf("text_separator").forGetter(FlexNode::textSeparator)
    ).apply(i, FlexNode::new));

    public FlexNode {
        children = List.copyOf(children);
    }

    @Override
    public MapCodec<FlexNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_flex";
    }

    private void style(Element element, String css) {
        element.setClassName("card_flex " + css);
        element.setInlineStyleProperty("display", "flex");
        element.setInlineStyleProperty("flex-direction", "column".equals(this.direction) ? "column" : "row");
        element.setInlineStyleProperty("flex-wrap", this.wrap ? "wrap" : "nowrap");
    }

    @Override
    public Element render(CardRenderer.Render view) {
        if (!this.source.isBlank() && this.indices(view).isEmpty()) return null;
        if (!this.source.isBlank() && this.pageSize > 0)
            return CardPagination.render(view, view.stateKey(), this.indices(view), this.pageSize, true, (parent, page) -> {
                this.style(parent, view.css());
                for (int index : page) this.renderChildren(view.loop(index), parent);
            });
        Element element = view.element("div", "");
        this.style(element, view.css());
        if (this.source.isBlank()) this.renderChildren(view, element);
        else for (int index : this.indices(view)) this.renderChildren(view.loop(index), element);
        return element;
    }

    @Override
    public String text(CardRenderer.Text view) {
        String separator = this.textSeparator.orElse(view.separator());
        if (this.source.isBlank()) return this.textChildren(view, separator);
        return String.join(separator, this.indices(view.context(), view.indices()).stream()
                .map(index -> CardRenderer.text(this.children, view.template(), view.context(), CardRenderer.loop(view.indices(), index), separator))
                .filter(text -> !text.isBlank()).toList());
    }
}
