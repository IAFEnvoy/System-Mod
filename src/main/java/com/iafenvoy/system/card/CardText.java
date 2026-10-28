package com.iafenvoy.system.card;

import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;

import java.util.List;

/**
 * Deferred item names and prices are resolved by client presentation, never during DTO construction.
 */
public sealed interface CardText permits CardText.Items, CardText.Price {
    record Items(List<ItemPreviewOutput> items) implements CardText {
        public Items {
            items = List.copyOf(items);
        }
    }

    record Price(CommerceCatalog.Price price, boolean includeItems) implements CardText {
    }
}
