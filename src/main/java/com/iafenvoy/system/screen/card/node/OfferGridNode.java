package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.output.ShelfEntry;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.Collections;
import java.util.List;

/**
 * Shelf of catalogue entries shared by the shop and lottery catalogues: fixed-column cells bound to the card's own
 * actions. {@code maxRow} limits the visible rows and lets the rest scroll inside the card.
 */
public record OfferGridNode(String entries, int columns, int maxRow, int cell) implements CardNode {
    private static final int MAX_CELL_ITEMS = 2;
    private static final int GAP = 6;

    public static final MapCodec<OfferGridNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("entries", "{{entries}}").forGetter(OfferGridNode::entries),
            Codec.INT.optionalFieldOf("columns", 4).forGetter(OfferGridNode::columns),
            Codec.INT.optionalFieldOf("max_row", 3).forGetter(OfferGridNode::maxRow),
            Codec.INT.optionalFieldOf("cell", 104).forGetter(OfferGridNode::cell)
    ).apply(i, OfferGridNode::new));

    public OfferGridNode {
        columns = Math.max(1, columns);
        cell = Math.max(48, cell);
    }

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
        if (source == null || source.isEmpty()) return null;
        Element grid = view.element("div", "");
        grid.setInlineStyleProperty("display", "grid");
        // Equal fractions fill the card width; `cell` is the row height, so only the column count is fixed.
        grid.setInlineStyleProperty("grid-template-columns",
                String.join(" ", Collections.nCopies(this.columns, "1fr")));
        if (this.maxRow > 0) {
            int height = this.maxRow * this.cell + Math.max(0, this.maxRow - 1) * GAP;
            grid.setInlineStyleProperty("max-height", height + "px");
            grid.setInlineStyleProperty("overflow", "auto");
        }
        for (int index = 0; index < source.size(); index++) {
            if (!(source.get(index) instanceof ShelfEntry entry)) continue;
            Element cell = AssistantView.element(view.document(), "div", "offer_cell", "");
            cell.setInlineStyleProperty("height", this.cell + "px");
            Element items = AssistantView.element(view.document(), "div", "offer_cell_items", "");
            // Each preview carries its own tooltip lines, so a cell can show prizes with different odds.
            List<ItemPreviewOutput> previews = entry.items();
            for (int item = 0; item < previews.size() && item < MAX_CELL_ITEMS; item++) {
                Element slot = AssistantItems.entry(view.document(), previews.get(item), entry.tooltip(item));
                if (slot != null) items.appendChild(slot);
            }
            if (!items.children.isEmpty()) cell.appendChild(items);
            cell.appendChild(AssistantView.element(view.document(), "div", "offer_cell_label", LegacyText.formatted(entry.label())));
            // A restored card has no live callbacks; the cell then renders as a plain label.
            Runnable callback = view.context().callbacks().get("actions." + index + ".click");
            if (callback != null) cell.addEventListener("click", event -> callback.run());
            grid.appendChild(cell);
        }
        return grid;
    }
}
