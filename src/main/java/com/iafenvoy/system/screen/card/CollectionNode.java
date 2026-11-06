package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.data.card.CardContext;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Layouts select original indices before slicing; callbacks and nested bindings keep those indices.
 */
public interface CollectionNode extends ChildrenNode {
    String source();

    String filter();

    default List<Integer> indices(CardRenderer.Render view) {
        String key = CardRenderer.binding(this.source(), view.indices());
        List<Integer> selected = view.selections().get(key);
        return (selected == null ? this.indices(view.context(), view.indices()).stream() : selected.stream())
                .filter(index -> CardRenderer.enabled(this.filter(), view.context(), view.loop(index).indices())).toList();
    }

    default List<Integer> indices(CardContext context, List<Integer> outer) {
        int count = Math.max(0, (int) context.states().getDouble(CardRenderer.binding(this.source(), outer)));
        return IntStream.range(0, count).filter(index -> CardRenderer.enabled(this.filter(), context, CardRenderer.loop(outer, index))).boxed().toList();
    }
}
