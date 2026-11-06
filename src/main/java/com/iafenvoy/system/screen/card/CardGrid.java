package com.iafenvoy.system.screen.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * One grid definition owns both its column layout and page capacity, including compact notification mode.
 */
public record CardGrid(int columns, int maxRow, int cell) {
    private static final int GAP = 6;
    // Shared CSS uses 10px padding and a 1px border on each side of the grid.
    private static final int INSET = 22;

    public static final MapCodec<CardGrid> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.lenientOptionalFieldOf("columns", 6).forGetter(CardGrid::columns),
            Codec.INT.lenientOptionalFieldOf("max_row", 3).forGetter(CardGrid::maxRow),
            Codec.INT.lenientOptionalFieldOf("cell", 104).forGetter(CardGrid::cell)
    ).apply(i, CardGrid::new));

    public CardGrid {
        columns = Math.max(1, columns);
        maxRow = Math.max(1, maxRow);
        cell = Math.max(48, cell);
    }

    public <T> Element render(CardRenderer.Render view, String key, List<T> entries, String css,
                              BiConsumer<Element, T> content) {
        boolean compact = CardRenderer.enabled("notification", view.context(), List.of())
                || CardRenderer.enabled("simple", view.context(), List.of());
        int columns = compact ? Math.min(3, this.columns) : this.columns;
        return CardPagination.render(view, key, entries, columns * this.maxRow, true, (grid, page) -> {
            grid.setClassName(css + " card_page_grid");
            grid.setInlineStyleProperty("grid-template-columns", String.join(" ", Collections.nCopies(columns, "1fr")));
            grid.setInlineStyleProperty("grid-template-rows", String.join(" ", Collections.nCopies(this.maxRow, this.cell + "px")));
            // Reserve every row, including an empty/partial last page, without querying layout or scroll limits.
            String height = (this.maxRow * this.cell + (this.maxRow - 1) * GAP + INSET) + "px";
            grid.setInlineStyleProperty("height", height);
            grid.setInlineStyleProperty("min-height", height);
            for (T entry : page) content.accept(grid, entry);
        });
    }
}
