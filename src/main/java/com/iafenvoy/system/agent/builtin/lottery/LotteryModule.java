package com.iafenvoy.system.agent.builtin.lottery;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.input.LotteryQueryInput;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.output.LotteryPoolOutput;
import com.iafenvoy.system.agent.builtin.output.LotteryPrizeOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.commerce.LotteryPool;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.data.SelectionCardData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registered commands and semantic examples browse pools without drawing or paying.
 */
public final class LotteryModule extends BuiltinModule<LotteryQueryInput> {
    static final int PAGE_SIZE = 8;

    public LotteryModule(ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview) {
        super(LotteryQueryInput.CODEC, "Browse lottery pools and their single-draw costs. Viewing never draws or pays.", List.of(preview), true, false, ExecutionPolicy.quick(1, 0));
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

    static LotteryPoolOutput summary(LotteryPool pool, CommercePreviewOutput price, boolean includePrizes, String revision) {
        // Browsing samples one prize page; the detail view already sends its own paged prize cards.
        long totalWeight = pool.totalWeight();
        List<LotteryPrizeOutput> prizes = includePrizes ? pool.entries().stream().limit(PAGE_SIZE)
                .map(entry -> new LotteryPrizeOutput(entry.reward(), entry.weight(), totalWeight)).toList() : List.of();
        return new LotteryPoolOutput(pool.id(), pool.label(), Optional.of(price.price()), pool.entries().size(), price.available(), prizes, price.quoteRef(), revision);
    }

    @Override
    public AgentResult execute(ExecutionContext context, LotteryQueryInput input) throws Exception {
        String query = input.query().toLowerCase(Locale.ROOT);
        CommerceCatalog catalog = context.catalog();
        List<LotteryPool> matches = catalog.lotteries().stream().filter(pool -> pool.id().toString().contains(query)
                || pool.label().toLowerCase(Locale.ROOT).contains(query)).toList();
        List<CardUpdate> cards = new ArrayList<>();
        List<LotteryPool> page = matches.stream().skip(input.offset()).limit(PAGE_SIZE).toList();
        List<CommercePreviewOutput> prices = page.isEmpty() ? List.of() : context.tool(BuiltinTools.COMMERCE_PREVIEW, "prices",
                new CommercePreviewInput(List.of(), page.stream().map(LotteryPool::id).toList(), false, catalog.revision()));
        for (int index = 0; index < page.size(); index++) {
            LotteryPool pool = page.get(index);
            cards.add(CardUpdate.show(summary(pool, prices.get(index), true, catalog.revision())));
        }
        if (matches.size() - input.offset() > PAGE_SIZE)
            cards.add(CardUpdate.show(SelectionCardData.fromActions(List.of(
                    new AgentResult.Action("system.action.next", new LotteryQueryInput(query, input.offset() + PAGE_SIZE))))));
        return new AgentResult(AgentResult.Status.SUCCEEDED, cards.isEmpty() ? "system.result.lottery_empty" : "system.result.lottery", "", cards);
    }
}
