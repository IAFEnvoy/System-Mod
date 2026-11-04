package com.iafenvoy.system.module.builtin.lottery;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AbstractAgentModule;
import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.input.LotteryQueryInput;
import com.iafenvoy.system.data.output.*;
import net.minecraft.core.Holder;

import java.util.*;

/**
 * Registered commands and semantic examples browse pools without drawing or paying. Browsing renders one shelf
 * card; a cell click opens the server-owned odds of that single pool.
 */
public final class LotteryModule extends AbstractAgentModule<LotteryQueryInput> {
    private final CommerceCatalogs catalogs;
    static final int MAX_POOLS = 128;
    static final int MAX_PRIZES = 64;
    static final int TOOLTIP_PRIZES = 10;

    public LotteryModule(CommerceCatalogs catalogs) {
        super(LotteryQueryInput.CODEC, "Browse lottery pools and their single-draw costs. Viewing never draws or pays.", List.of(), true, false, ExecutionPolicy.quick(1, 0));
        this.catalogs = catalogs;
    }

    @Override
    public ModuleRouting<LotteryQueryInput> routing() {
        return ModuleRouting.<LotteryQueryInput>defaults()
                .button(a -> new LotteryQueryInput(a.text("query"), a.number(0, "offset")))
                .command(AgentCommand.argument("[query]", text -> new LotteryQueryInput(text, 0), "lottery", "抽奖"))
                .semantic(RoutingProfile.read(text -> new LotteryQueryInput("", 0),
                        List.of("打开抽奖界面", "看看有哪些奖池", "我要抽奖", "查询抽奖活动"),
                        List.of("Open the lottery", "Show available prize pools", "I want to draw a prize", "Browse lottery pools")))
                .shortcut("system.action.view_lottery", new LotteryQueryInput("", 0));
    }

    /**
     * The detail card carries every prize it can show; the label already reports the truncated count.
     */
    static LotteryPoolOutput summary(Holder<LotteryPool> holder, CommercePreviewOutput price, String revision) {
        LotteryPool pool = holder.value();
        long totalWeight = pool.totalWeight();
        List<LotteryPrizeOutput> prizes = pool.entries().stream().limit(MAX_PRIZES)
                .map(entry -> new LotteryPrizeOutput(entry.reward(), entry.weight(), totalWeight)).toList();
        return new LotteryPoolOutput(HolderHelper.id(holder), pool.label(), Optional.of(price.price()), pool.entries().size(), price.available(), prizes, price.quoteRef(), revision);
    }

    /**
     * Shelf cells carry the whole odds list of the pool: up to ten rarest prizes, each with its items. The list is
     * built from the most likely of those down to the rarest, matching how the tooltip reads.
     */
    static LotteryCatalogOutput.Pool entry(Holder<LotteryPool> holder) {
        LotteryPool pool = holder.value();
        long totalWeight = pool.totalWeight();
        List<LotteryPool.Entry> ranked = pool.entries().stream()
                .sorted(Comparator.comparingDouble(value -> totalWeight <= 0 ? 0.0 : (double) value.weight() / totalWeight))
                .limit(TOOLTIP_PRIZES).toList();
        List<LotteryCatalogOutput.Prize> prizes = new ArrayList<>();
        for (int index = ranked.size() - 1; index >= 0; index--) {
            LotteryPool.Entry entry = ranked.get(index);
            prizes.add(new LotteryCatalogOutput.Prize(entry.reward().value().label(), LotteryPrizeOutput.chance(entry.weight(), totalWeight),
                    entry.reward().value().items().stream().map(ItemPreviewOutput::of).toList()));
        }
        return new LotteryCatalogOutput.Pool(HolderHelper.id(holder).toString(), pool.label(), pool.entries().size(), prizes);
    }

    @Override
    public AgentResult execute(ExecutionContext context, LotteryQueryInput input) {
        String query = input.query().toLowerCase(Locale.ROOT);
        CommerceCatalog catalog = this.catalogs.available(context.player());
        List<Holder<LotteryPool>> matches = catalog.lotteries().stream().filter(holder -> HolderHelper.id(holder).toString().contains(query)
                || holder.value().label().getString().toLowerCase(Locale.ROOT).contains(query)).toList();
        // Prices are evaluated only for the pool the player actually opens.
        List<LotteryCatalogOutput.Pool> pools = matches.stream().limit(MAX_POOLS).map(LotteryModule::entry).toList();
        if (pools.isEmpty()) return AgentResult.ok("system.result.lottery_empty");
        return AgentResult.data("system.result.lottery",
                new LotteryCatalogOutput(catalog.revision(), input.query(), pools, matches.size() > MAX_POOLS));
    }
}
