package com.iafenvoy.system.screen.card.node.layout;

import com.iafenvoy.system.screen.card.*;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.Collections;
import java.util.List;

/**
 * Only the visible page creates cells. Track dimensions never query AUI layout or scrolling.
 */
public record GridNode(String source, String filter, int columns, int compactColumns, int maxRow, int cell,
                       List<CardTemplate.Node> children) implements CollectionNode, TextProjectingNode {
    public static final MapCodec<GridNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("source", "").forGetter(GridNode::source),
            Codec.STRING.lenientOptionalFieldOf("filter", "").forGetter(GridNode::filter),
            Codec.INT.lenientOptionalFieldOf("columns", 6).forGetter(GridNode::columns),
            Codec.INT.lenientOptionalFieldOf("compact_columns", 3).forGetter(GridNode::compactColumns),
            Codec.INT.lenientOptionalFieldOf("max_row", 3).forGetter(GridNode::maxRow),
            Codec.INT.lenientOptionalFieldOf("cell", 104).forGetter(GridNode::cell),
            CardTemplate.NODES.fieldOf("children").forGetter(GridNode::children)
    ).apply(i, GridNode::new));

    public GridNode {
        children = List.copyOf(children);
        columns = Math.max(1, columns);
        compactColumns = Math.max(1, compactColumns);
        maxRow = Math.max(1, maxRow);
        cell = Math.max(1, cell);
    }

    @Override
    public MapCodec<GridNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_grid";
    }

    private int columns(CardRenderer.Render view) {
        return CardRenderer.enabled("full", view.context(), List.of()) ? this.columns : Math.min(this.columns, this.compactColumns);
    }

    private void style(Element grid, CardRenderer.Render view, int rows) {
        grid.setClassName(view.css() + " card_page_grid");
        grid.setInlineStyleProperty("display", "grid");
        grid.setInlineStyleProperty("grid-template-columns", String.join(" ", Collections.nCopies(this.columns(view), "minmax(0, 1fr)")));
        if (rows > 0) {
            grid.setInlineStyleProperty("grid-template-rows", String.join(" ", Collections.nCopies(rows, this.cell + "px")));
            String height = (rows * this.cell + (rows - 1) * 6 + 22) + "px";
            grid.setInlineStyleProperty("height", height);
            grid.setInlineStyleProperty("min-height", height);
        }
    }

    @Override
    public Element render(CardRenderer.Render view) {
        if (this.source.isBlank()) {
            Element root = view.element("div", "");
            this.style(root, view, 0);
            this.renderChildren(view, root);
            return root;
        }
        List<Integer> indices = this.indices(view);
        if (indices.isEmpty()) return null;
        int columns = this.columns(view);
        int pageSize = columns * this.maxRow;
        // Only a single-page result shrinks; every page of a larger result reserves the same rows.
        int rows = indices.size() <= pageSize ? Math.ceilDiv(indices.size(), columns) : this.maxRow;
        return CardPagination.render(view, view.stateKey(), indices, pageSize, true, (grid, page) -> {
            this.style(grid, view, rows);
            for (int index : page) {
                Element cell = AssistantView.element(view.document(), "div", "card_grid_cell", "");
                cell.setInlineStyleProperty("height", this.cell + "px");
                this.renderChildren(view.loop(index), cell);
                grid.appendChild(cell);
            }
        });
    }

    @Override
    public String text(CardRenderer.Text view) {
        if (this.source.isBlank()) return this.textChildren(view, view.separator());
        return String.join(view.separator(), this.indices(view.context(), view.indices()).stream()
                .map(index -> CardRenderer.text(this.children, view.template(), view.context(), CardRenderer.loop(view.indices(), index), view.separator()))
                .filter(text -> !text.isBlank()).toList());
    }
}
