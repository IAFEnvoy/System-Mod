package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A bounded search or targeted revalidation; empty browsing never expands generated stock.
 */
public record ShopRequest(String query, Optional<ShopTrade> trade, Set<Identifier> ids,
                          Optional<ItemSearchBatch> candidates) {
    public static final int MAX_RESULTS = 128;
    public static final ShopRequest EMPTY = new ShopRequest("", Optional.empty(), Set.of(), Optional.empty());

    public ShopRequest {
        query = query.strip().toLowerCase(Locale.ROOT);
        if (query.length() > 128 || ids.size() > MAX_RESULTS)
            throw new IllegalArgumentException("Shop request too large");
        ids = Set.copyOf(ids);
    }

    public static ShopRequest search(String query, ShopTrade trade) {
        return search(query, trade, Optional.empty());
    }

    public static ShopRequest search(String query, ShopTrade trade, Optional<ItemSearchBatch> candidates) {
        return new ShopRequest(query, Optional.of(trade), Set.of(), candidates);
    }

    public static ShopRequest resolve(Set<Identifier> ids) {
        return new ShopRequest("", Optional.empty(), ids, Optional.empty());
    }
}
