package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.data.cost.CostDisplay;
import com.iafenvoy.saturn.data.cost.CostTarget;
import com.iafenvoy.saturn.data.cost.builtin.ItemCost;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.item.ItemSearches;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOffer;
import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.output.ShopCatalogOutput;
import com.iafenvoy.system.data.output.ShopPurchaseLimitOutput;
import com.iafenvoy.system.data.space.SpaceContentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Shared snapshot construction for separate purchase and buyback workflows.
 */
public final class ShopBrowsing {
    private static final int MAX_OFFERS = 128;
    private final CommerceCatalogs catalogs;
    private final CommerceTransactions transactions;
    private final ItemSearches searches;

    public ShopBrowsing(CommerceCatalogs catalogs, CommerceTransactions transactions, ItemSearches searches) {
        this.catalogs = catalogs;
        this.transactions = transactions;
        this.searches = searches;
    }

    public AgentResult browse(ExecutionContext context, ItemSearchQuery input, ShopTrade trade) {
        String query = input.query().strip().toLowerCase(Locale.ROOT);
        Optional<ItemSearchBatch> candidates = this.searches.candidates(context.player(), input);
        CommerceCatalogs.View view = this.transactions.shopView(context, ShopRequest.search(query, trade, candidates));
        CommerceCatalog catalog = view.catalog();
        List<ItemStack> stacks = candidates.map(batch -> batch.items().stream().map(ItemStackTemplate::create).toList()).orElse(List.of());
        Set<ItemStackTemplate> variants = candidates.map(batch -> Set.copyOf(batch.items())).orElse(Set.of());
        List<ShopOffer> matches = catalog.offers().stream()
                .filter(offer -> offer.value().trade() == trade)
                .filter(offer -> offer.searchOnly() || matches(offer, query) || matchesItems(offer, variants, stacks)).toList();
        List<ShopOffer> visible = matches.stream().limit(MAX_OFFERS).toList();
        Map<Identifier, ShopPurchaseLimitOutput> limits = this.transactions.shopLimits(context, visible);
        List<ShopOffer> buybacks = visible.stream().filter(offer -> offer.value().trade() == ShopTrade.BUYBACK).toList();
        Map<Identifier, CommerceCatalog.Price> requirements = buybacks.isEmpty() ? Map.of() : this.catalogs.access().read(context,
                player -> buybacks.stream().collect(Collectors.toMap(ShopOffer::id, offer ->
                        CommerceCosts.prepare(offer, player).getData())));
        List<ShopCatalogOutput.Offer> offers = visible.stream()
                .map(offer -> row(offer, requirements.get(offer.id()), Optional.ofNullable(limits.get(offer.id())),
                        !view.lockedOffers().contains(offer.id()))).toList();
        return AgentResult.data(trade == ShopTrade.BUYBACK ? "system.result.buyback" : "system.result.shop",
                new ShopCatalogOutput(catalog.shopRevision(), query, offers, matches.size() > MAX_OFFERS, Optional.of(trade),
                        query.isEmpty() && this.catalogs.searchOnly(context.player(), trade),
                        candidates.filter(ItemSearchBatch::hasMore).map(ItemSearchBatch::nextOffset)));
    }

    private static boolean matchesItems(ShopOffer offer, Set<ItemStackTemplate> variants, List<ItemStack> stacks) {
        ShopOfferEntry entry = offer.value();
        return ItemSearches.matches(variants, entry.items(), entry.contents())
                || entry.trade() == ShopTrade.BUYBACK && entry.costs().stream().filter(ItemCost.class::isInstance)
                .map(ItemCost.class::cast).anyMatch(cost -> stacks.stream().anyMatch(stack -> ItemMatcher.matches(cost.entries(), stack)));
    }

    private static boolean matches(ShopOffer offer, String query) {
        ShopOfferEntry entry = offer.value();
        return query.isEmpty() || offer.id().toString().contains(query)
                || entry.label().getString().toLowerCase(Locale.ROOT).contains(query)
                || entry.items().stream().anyMatch(item -> item.item().getRegisteredName().contains(query))
                || entry.contents().stream().anyMatch(content -> content.name().getString().toLowerCase(Locale.ROOT).contains(query)
                || SpaceContentType.of(content.content()).searchId(content.content()).toLowerCase(Locale.ROOT).contains(query))
                || entry.trade() == ShopTrade.BUYBACK && entry.costs().stream().filter(ItemCost.class::isInstance)
                .map(ItemCost.class::cast).anyMatch(cost -> matches(cost, query));
    }

    private static boolean matches(ItemCost cost, String query) {
        CostDisplay display = new CostTarget.Items(null, cost.entries()).display(1);
        return display.descriptions().stream().anyMatch(text -> text.getString().toLowerCase(Locale.ROOT).contains(query))
                || display.items().stream().anyMatch(item ->
                item.item().item().getRegisteredName().contains(query)
                        || item.item().create().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query));
    }

    private static ShopCatalogOutput.Offer row(ShopOffer offer, @Nullable CommerceCatalog.Price requirement,
                                               Optional<ShopPurchaseLimitOutput> limit, boolean available) {
        ShopOfferEntry entry = offer.value();
        List<Component> details = new ArrayList<>();
        if (requirement != null) {
            details.add(Component.translatable(entry.trade().key("cost")));
            requirement.items().forEach(item -> details.add(Component.translatable("system.ui.item_amount", item.item().create().getHoverName(), item.amount())));
            details.addAll(requirement.descriptions());
            details.add(Component.translatable(entry.trade().key("reward")));
            entry.items().forEach(item -> details.add(Component.translatable("system.ui.item_amount", item.create().getHoverName(), item.count())));
        }
        return new ShopCatalogOutput.Offer(offer.id().toString(), entry.label(),
                requirement == null ? entry.items().stream().map(ItemPreviewOutput::of).toList() : requirement.items(),
                entry.contents(), limit, available, entry.trade(), details);
    }
}
