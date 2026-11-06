package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.output.ShelfEntry;
import com.iafenvoy.system.screen.card.CardGrid;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Shelf of catalogue entries shared by the shop and lottery catalogues: fixed-column cells bound to the card's own
 * actions. The shared grid creates one page and keeps a fixed row area and navigation controls.
 */
public record OfferGridNode(String entries, CardGrid grid) implements CardNode {
    private static final int MAX_CELL_ITEMS = 2;

    public static final MapCodec<OfferGridNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("entries", "{{entries}}").forGetter(OfferGridNode::entries),
            CardGrid.CODEC.forGetter(OfferGridNode::grid)
    ).apply(i, OfferGridNode::new));

    @Override
    public MapCodec<OfferGridNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "offer_grid";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        List<?> source = view.context().get(CardRenderer.binding(this.entries, view.indices()), List.class);
        if (source == null) return null;
        List<Integer> indices = IntStream.range(0, source.size()).filter(index -> source.get(index) instanceof ShelfEntry).boxed().toList();
        return this.grid.render(view, "offers:" + CardRenderer.binding(this.entries, view.indices()), indices, view.css(), (parent, index) -> {
            ShelfEntry entry = (ShelfEntry) source.get(index);
            Element cell = AssistantView.element(view.document(), "div", "offer_cell", "");
            cell.setInlineStyleProperty("height", this.grid.cell() + "px");
            Element items = AssistantView.element(view.document(), "div", "offer_cell_items", "");
            // Each preview carries its own tooltip lines, so a cell can show prizes with different odds.
            List<ItemPreviewOutput> previews = entry.items();
            for (int item = 0; item < previews.size() && item < MAX_CELL_ITEMS; item++) {
                Element slot = AssistantItems.entry(view.document(), previews.get(item), entry.tooltip(item));
                if (slot != null) items.appendChild(slot);
            }
            if (!items.children.isEmpty()) cell.appendChild(items);
            cell.appendChild(AssistantView.element(view.document(), "div", "offer_cell_label", LegacyText.formatted(entry.label())));
            // Keep original indices across pages; restored cards still have no live callbacks.
            List<?> actions = view.context().get(CardContext.RESULT_ACTIONS, List.class);
            if (actions != null && index < actions.size() && actions.get(index) instanceof CardButton action)
                ActionsNode.bind(view, cell, action, index);
            parent.appendChild(cell);
        });
    }
}
