package com.iafenvoy.system.module.builtin.lottery;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.input.LotteryPreviewInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Optional;

/**
 * One pool's odds and a short-lived confirmation are produced from the same immutable catalog snapshot.
 */
public final class LotteryPreviewModule extends AbstractAgentModule<LotteryPreviewInput> {
    private final CommerceCatalogs catalogs;

    public LotteryPreviewModule(CommerceCatalogs catalogs, ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview) {
        super(LotteryPreviewInput.CODEC, "Preview one lottery pool's prizes, odds and cost. The player must separately confirm one draw.", List.of(preview), true, false, ExecutionPolicy.quick(1, 0));
        this.catalogs = catalogs;
    }

    @Override
    public ModuleRouting<LotteryPreviewInput> routing() {
        return ModuleRouting.<LotteryPreviewInput>defaults()
                .button(a -> new LotteryPreviewInput(a.identifier("pool", "poolId"), a.number(0, "offset"), a.optional("revision", "catalogRevision")))
                .command(AgentCommand.identifier("pool_id", id -> new LotteryPreviewInput(id, 0, Optional.empty()), "draw", "单抽"))
                .semantic(RoutingProfile.hints(
                        List.of("查看这个奖池的概率", "我要单抽这个奖池", "预览抽奖费用"),
                        List.of("Show this pool's odds", "Draw once from this pool", "Preview the lottery cost")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, LotteryPreviewInput input) throws Exception {
        CommerceCatalog catalog = this.catalogs.available(context.player());
        if (!input.catalogRevision().orElse(catalog.revision()).equals(catalog.revision()))
            throw new AgentException("STALE_QUOTE");
        Holder<LotteryPool> pool = catalog.lotteries().stream().filter(value -> HolderHelper.id(value).equals(input.poolId())).findFirst()
                .orElseThrow(() -> new AgentException("INVALID_INPUT"));
        CommercePreviewOutput price = context.tool(BuiltinTools.COMMERCE_PREVIEW, "quote",
                new CommercePreviewInput(List.of(), List.of(HolderHelper.id(pool)), true, catalog.revision())).getFirst();
        // One detail card carries the whole shelf; the previous per-prize cards would spam the transcript.
        return AgentResult.data("system.result.lottery_preview", LotteryModule.summary(pool, price, catalog.revision()));
    }
}
