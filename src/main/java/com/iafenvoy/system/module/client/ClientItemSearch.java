package com.iafenvoy.system.module.client;

import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.request.AgentRequest;
import com.iafenvoy.system.agent.request.CardActionRequest;
import com.iafenvoy.system.agent.request.InvokeRequest;
import com.iafenvoy.system.data.input.*;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.iafenvoy.system.util.CardEventValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Extract only read-only search selectors, preserving the original request and card correlation.
 */
public final class ClientItemSearch {
    private ClientItemSearch() {
    }

    public static Optional<ItemSearchQuery> query(AgentRequest request) {
        AgentAction action = request instanceof InvokeRequest invoke ? invoke.call() : null;
        if (request instanceof CardActionRequest card) try {
            CardEventValue event = CardEventValue.parse(card.value());
            String text = event.arguments().getOrDefault("query", "");
            int offset = Integer.parseInt(event.arguments().getOrDefault("offset", "0"));
            // References and variant selections already point at a validated server snapshot.
            if (event.arguments().containsKey("search_ref") || event.arguments().containsKey("selected"))
                return Optional.empty();
            if (event.action().equals(new ItemQueryInput("", 0).id())) action = new ItemQueryInput(text, offset);
            else if (event.action().equals(new ShopQueryInput("", 0).id())) action = new ShopQueryInput(text, offset);
            else if (event.action().equals(new BuybackQueryInput("", 0).id()))
                action = new BuybackQueryInput(text, offset);
            else if (event.action().equals(new LotteryQueryInput("", 0).id()))
                action = new LotteryQueryInput(text, offset, Integer.parseInt(event.arguments().getOrDefault("item_offset", "0")), Optional.empty());
            else if (event.action().equals(new ItemSourceQueryInput("", "").id()))
                action = new ItemSourceQueryInput(event.arguments().getOrDefault("item_id", ""), event.arguments().getOrDefault("scope", ""), offset, Optional.empty());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        if (action instanceof SpaceTakeInput take && !take.entry().isBlank()) return Optional.empty();
        if (action instanceof ItemSourceQueryInput source) {
            Identifier id = Identifier.tryParse(source.itemId());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return Optional.empty();
        }
        return action instanceof ItemSearchQuery query && query.searchRef().isEmpty() && !query.query().isBlank()
                ? Optional.of(query) : Optional.empty();
    }

    public static CompletableFuture<ItemSearchBatch> search(ItemSearchQuery query) {
        return query instanceof SpaceTakeInput ? ClientModuleCaches.ITEM_SEARCH.searchStored(query.query())
                : ClientModuleCaches.ITEM_SEARCH.search(query.query(), query.searchOffset());
    }
}
