package com.iafenvoy.system.agent.builtin.lottery;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.input.LotteryPreviewInput;
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
import com.iafenvoy.system.registry.SystemActionTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Paged prize odds and a short-lived confirmation are produced from the same immutable catalog snapshot.
 */
public final class LotteryPreviewModule extends BuiltinModule<LotteryPreviewInput> {
    public LotteryPreviewModule(ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview) {
        super(SystemActionTypes.LOTTERY_PREVIEW_ACTION.get(), "Preview one lottery pool's prizes, odds and cost. The player must separately confirm one draw.",
                LotteryPreviewInput.CODEC, List.of(preview), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<LotteryPreviewInput> routing() {
        return ModuleRouting.<LotteryPreviewInput>defaults()
                .button(a -> new LotteryPreviewInput(a.identifier("pool", "poolId"), a.number(0, "offset"), a.optional("revision", "catalogRevision")))
                .command(AgentCommand.argument("<pool_id>", text -> new LotteryPreviewInput(CommandArguments.identifier(text), 0, Optional.empty()), "draw", "单抽"))
                .semantic(RoutingProfile.hints(
                        List.of("查看这个奖池的概率", "我要单抽这个奖池", "预览抽奖费用"),
                        List.of("Show this pool's odds", "Draw once from this pool", "Preview the lottery cost")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, LotteryPreviewInput input) throws Exception {
        CommerceCatalog catalog = context.catalog();
        if (!input.catalogRevision().orElse(catalog.revision()).equals(catalog.revision()))
            throw new AgentException("STALE_QUOTE");
        LotteryPool pool = catalog.lotteries().stream().filter(value -> value.id().equals(input.poolId())).findFirst()
                .orElseThrow(() -> new AgentException("INVALID_INPUT"));
        CommercePreviewOutput price = context.tool(BuiltinTools.COMMERCE_PREVIEW, "quote",
                new CommercePreviewInput(List.of(), List.of(pool.id()), true, catalog.revision())).getFirst();
        LotteryPoolOutput summary = LotteryModule.summary(pool, price, false, catalog.revision());
        List<CardUpdate> cards = new ArrayList<>();
        cards.add(CardUpdate.show(summary));
        long totalWeight = pool.totalWeight();
        for (LotteryPool.Entry entry : pool.entries().stream().skip(input.offset()).limit(LotteryModule.PAGE_SIZE).toList())
            cards.add(CardUpdate.show(new LotteryPrizeOutput(entry.reward(), entry.weight(), totalWeight)));
        if (pool.entries().size() - input.offset() > LotteryModule.PAGE_SIZE)
            cards.add(CardUpdate.show(SelectionCardData.fromActions(List.of(
                    new AgentResult.Action("system.action.next", SystemActionTypes.LOTTERY_PREVIEW_ACTION.get(),
                            new LotteryPreviewInput(pool.id(), input.offset() + LotteryModule.PAGE_SIZE, Optional.of(catalog.revision())))))));
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.lottery_preview", "", cards);
    }
}
