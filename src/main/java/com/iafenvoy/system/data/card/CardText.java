package com.iafenvoy.system.data.card;

import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.output.ShelfEntry;

import java.util.List;

/**
 * Deferred item names and prices are resolved by client presentation, never during DTO construction.
 */
public sealed interface CardText permits CardText.Items, CardText.Price, CardText.ShelfTooltip {
    record Items(List<ItemPreviewOutput> items) implements CardText {
    }

    record Price(CommerceCatalog.Price price, boolean includeItems) implements CardText {
    }

    record ShelfTooltip(ShelfEntry entry, int index) implements CardText {
    }
}
