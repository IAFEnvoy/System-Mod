package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.LotteryPreviewInput;
import com.iafenvoy.system.agent.builtin.input.LotteryQueryInput;
import com.iafenvoy.system.agent.builtin.output.LotteryPoolOutput;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.commerce.LotteryPool;
import com.iafenvoy.system.agent.commerce.PaymentType;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registered commands and semantic examples browse pools without drawing or paying.
 */
public final class LotteryModule extends BuiltinModule<LotteryQueryInput> {
    public LotteryModule() {
        super(ResultTypes.LOTTERY_ACTION, "Browse lottery pools and their single-draw costs. Viewing never draws or pays.",
                LotteryQueryInput.CODEC, List.of(), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<LotteryQueryInput> routing() {
        return ModuleRouting.<LotteryQueryInput>defaults()
                .command(AgentCommand.argument("[query]", text -> new LotteryQueryInput(text, 0), "lottery", "抽奖"))
                .semantic(RoutingProfile.read(text -> new LotteryQueryInput("", 0),
                        List.of("打开抽奖界面", "看看有哪些奖池", "我要抽奖", "查询抽奖活动"),
                        List.of("Open the lottery", "Show available prize pools", "I want to draw a prize", "Browse lottery pools")))
                .shortcut("system.action.view_lottery", new LotteryQueryInput("", 0));
    }

    static LotteryPoolOutput summary(ExecutionContext context, LotteryPool pool) {
        boolean available = pool.price().filter(price -> price.type() == PaymentType.SYSTEM_COIN).isEmpty()
                || context.coinPayment().available();
        return new LotteryPoolOutput(pool.id(), pool.label(), pool.price(), pool.entries().size(), available);
    }

    @Override
    public AgentResult execute(ExecutionContext context, LotteryQueryInput input) {
        String query = input.query().toLowerCase(Locale.ROOT);
        CommerceCatalog catalog = context.catalog();
        List<LotteryPool> matches = catalog.lotteries().stream().filter(pool -> pool.id().toString().contains(query)
                || pool.label().toLowerCase(Locale.ROOT).contains(query)).toList();
        List<AgentResult.Card> cards = new ArrayList<>();
        for (LotteryPool pool : matches.stream().skip(input.offset()).limit(8).toList())
            cards.add(new AgentResult.Card("system.result.lottery_pool", ResultTypes.LOTTERY_POOL.of(summary(context, pool)), List.of(
                    new AgentResult.Action("system.action.lottery_preview", ResultTypes.LOTTERY_PREVIEW_ACTION,
                            new LotteryPreviewInput(pool.id(), 0, Optional.of(catalog.revision()))))));
        if (matches.size() - input.offset() > 8)
            cards.add(new AgentResult.Card("system.result.page", ResultTypes.EMPTY.of(EmptyInput.INSTANCE), List.of(
                    new AgentResult.Action("system.action.next", ResultTypes.LOTTERY_ACTION, new LotteryQueryInput(query, input.offset() + 8)))));
        return new AgentResult("SUCCEEDED", cards.isEmpty() ? "system.result.lottery_empty" : "system.result.lottery", "", cards);
    }
}
