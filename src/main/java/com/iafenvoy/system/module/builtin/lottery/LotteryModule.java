package com.iafenvoy.system.module.builtin.lottery;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.assistant.item.ItemSearches;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.input.LotteryQueryInput;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.data.output.*;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.*;

/**
 * Registered commands and semantic examples browse pools without drawing or paying. Browsing renders one shelf
 * card; a cell click opens the server-owned odds of that single pool.
 */
public final class LotteryModule extends AbstractAgentModule<LotteryQueryInput> {
    private final CommerceCatalogs catalogs;
    private final ItemSearches searches;
    static final int MAX_POOLS = 128;
    static final int MAX_PRIZES = 64;
    static final int TOOLTIP_PRIZES = 10;

    public LotteryModule(CommerceCatalogs catalogs, ItemSearches searches) {
        super(LotteryQueryInput.CODEC, "Browse lottery pools and their single-draw costs. Viewing never draws or pays.", List.of(), true, false, ExecutionPolicy.quick(1, 0));
        this.catalogs = catalogs;
        this.searches = searches;
    }

    @Override
    public ModuleRouting<LotteryQueryInput> routing() {
        return ModuleRouting.<LotteryQueryInput>defaults()
                .button(a -> new LotteryQueryInput(a.text("query"), a.number(0, "offset"), a.number(0, "item_offset"), a.optional("search_ref")))
                .command(AgentCommand.argument("[query]", text -> new LotteryQueryInput(text, 0), "lottery", "抽奖"))
                .semantic(RoutingProfile.read(text -> new LotteryQueryInput("", 0),
                        List.of("打开抽奖界面", "看看有哪些奖池", "我要抽奖", "查询抽奖活动"),
                        List.of("Open the lottery", "Show available prize pools", "I want to draw a prize", "Browse lottery pools")))
                .shortcut("system.action.view_lottery", new LotteryQueryInput("", 0));
    }

    static int offset(int requested, int count, int size) {
        return Math.min(Math.max(0, requested) / size, Math.max(0, count - 1) / size) * size;
    }

    /**
     * Each batch uses all eligible rewards' weight, never just the visible subset's weight.
     */
    static LotteryPoolOutput summary(Holder<LotteryPool> holder, CommercePreviewOutput price, String revision, int requested) {
        LotteryPool pool = holder.value();
        Map<Identifier, Integer> wins = price.lottery().map(LotteryStatusOutput::wins).orElse(Map.of());
        long totalWeight = pool.eligible(wins).stream().mapToLong(LotteryPool.Entry::weight).sum();
        int offset = offset(requested, pool.entries().size(), MAX_PRIZES);
        List<LotteryPrizeOutput> prizes = pool.entries().stream().skip(offset).limit(MAX_PRIZES)
                .map(entry -> new LotteryPrizeOutput(entry.reward(), entry.remaining(wins) == 0 ? 0 : entry.weight(), totalWeight,
                        entry.maxWins(), wins.getOrDefault(entry.reward().id(), 0))).toList();
        return new LotteryPoolOutput(HolderHelper.id(holder), pool.label(), Optional.of(price.price()), pool.entries().size(), price.available(), prizes, price.quoteRef(), revision, price.conditionMet(), offset, price.lottery());
    }

    /**
     * Shelf cells carry the whole odds list of the pool: up to ten rarest prizes, each with its items. The list is
     * built from the most likely of those down to the rarest, matching how the tooltip reads.
     */
    static LotteryCatalogOutput.Pool entry(Holder<LotteryPool> holder, boolean available) {
        LotteryPool pool = holder.value();
        long totalWeight = pool.totalWeight();
        List<LotteryPool.Entry> ranked = pool.entries().stream()
                .sorted(Comparator.comparingDouble(value -> totalWeight <= 0 ? 0.0 : (double) value.weight() / totalWeight))
                .limit(TOOLTIP_PRIZES).toList();
        List<LotteryCatalogOutput.Prize> prizes = new ArrayList<>();
        for (int index = ranked.size() - 1; index >= 0; index--) {
            LotteryPool.Entry entry = ranked.get(index);
            prizes.add(new LotteryCatalogOutput.Prize(entry.reward().value().label(), LotteryPrizeOutput.chance(entry.weight(), totalWeight),
                    entry.reward().value().items().stream().map(ItemPreviewOutput::of).toList(), entry.reward().value().contents(), entry.maxWins()));
        }
        return new LotteryCatalogOutput.Pool(HolderHelper.id(holder).toString(), pool.label(), pool.entries().size(), prizes, available);
    }

    @Override
    public AgentResult execute(ExecutionContext context, LotteryQueryInput input) {
        String query = input.query().strip().toLowerCase(Locale.ROOT);
        Optional<ItemSearchBatch> candidates = this.searches.candidates(context.player(), input);
        Set<ItemStackTemplate> variants = candidates.map(batch -> Set.copyOf(batch.items())).orElse(Set.of());
        CommerceCatalogs.View view = this.catalogs.view(context);
        CommerceCatalog catalog = view.catalog();
        List<Holder<LotteryPool>> matches = catalog.lotteries().stream().filter(holder -> HolderHelper.id(holder).toString().contains(query)
                || holder.value().label().getString().toLowerCase(Locale.ROOT).contains(query)
                || holder.value().entries().stream().anyMatch(entry -> ItemSearches.matches(variants, entry.reward().value().items(), entry.reward().value().contents()))).toList();
        // Prices are evaluated only for the pool the player actually opens.
        int offset = offset(input.offset(), matches.size(), MAX_POOLS);
        List<LotteryCatalogOutput.Pool> pools = matches.stream().skip(offset).limit(MAX_POOLS)
                .map(holder -> entry(holder, !view.lockedLotteries().contains(HolderHelper.id(holder)))).toList();
        if (pools.isEmpty() && candidates.filter(ItemSearchBatch::hasMore).isEmpty())
            return AgentResult.ok("system.result.lottery_empty");
        return AgentResult.data("system.result.lottery",
                new LotteryCatalogOutput(catalog.revision(), input.query(), pools, offset, matches.size(), input.searchRef(),
                        input.itemOffset(), candidates.filter(ItemSearchBatch::hasMore).map(ItemSearchBatch::nextOffset)));
    }
}
