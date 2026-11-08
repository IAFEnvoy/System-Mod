package com.iafenvoy.system.screen.card;

import com.sighs.apricityui.init.Element;

import java.util.List;

/**
 * Child traversal shares the same context and never introduces another parameter namespace.
 */
public interface ChildrenNode extends CardNode {
    List<CardTemplate.Node> children();

    default void renderChildren(CardRenderer.Render view, Element parent) {
        CardRenderer.render(view, parent, this.children());
    }

    default String textChildren(CardRenderer.Text view, String separator) {
        return CardRenderer.text(this.children(), view.template(), view.context(), view.indices(), separator);
    }
}
