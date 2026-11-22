package com.iafenvoy.system.module.builtin.lottery;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.command.AssistantCommandSource;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.input.LotteryPreviewInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * One pool's odds and a short-lived confirmation are produced from the same immutable catalog snapshot.
 */
public final class LotteryPreviewModule extends AbstractAgentModule<LotteryPreviewInput> {
    private final CommerceCatalogs catalogs;

    public LotteryPreviewModule(CommerceCatalogs catalogs, ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>, ExecutionContext> preview) {
        super(LotteryPreviewInput.CODEC, "Preview one lottery pool's prizes, base odds, pity, quota and total cost for an allowed draw count. The player must separately confirm payment.", List.of(preview), true, false, ExecutionPolicy.quick(1, 0));
        this.catalogs = catalogs;
    }

    @Override
    public ModuleRouting<LotteryPreviewInput> routing() {
        return ModuleRouting.<LotteryPreviewInput>defaults()
                .button(a -> new LotteryPreviewInput(a.identifier("pool", "poolId"), a.number(0, "offset"), a.optional("revision", "catalogRevision"), a.number(1, "count")))
                .command(new AgentCommand(List.of("draw", "单抽", "抽奖预览"), name -> LiteralArgumentBuilder.<AssistantCommandSource>literal(name)
                        .then(RequiredArgumentBuilder.<AssistantCommandSource, Identifier>argument("pool_id", IdentifierArgument.id())
                                .executes(command -> command.getSource().action(new LotteryPreviewInput(command.getArgument("pool_id", Identifier.class), 0, Optional.empty())))
                                .then(RequiredArgumentBuilder.<AssistantCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 100))
                                        .executes(command -> command.getSource().action(new LotteryPreviewInput(command.getArgument("pool_id", Identifier.class), 0,
                                                Optional.empty(), IntegerArgumentType.getInteger(command, "count"))))))))
                .semantic(RoutingProfile.hints(
                        List.of("查看这个奖池的概率", "我要单抽这个奖池", "预览抽奖费用"),
                        List.of("Show this pool's odds", "Draw once from this pool", "Preview the lottery cost")));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, LotteryPreviewInput input) throws Exception {
        CommerceCatalog catalog = this.catalogs.view(context).catalog();
        if (!input.catalogRevision().orElse(catalog.revision()).equals(catalog.revision()))
            throw new AgentException(AgentFailure.STALE_QUOTE);
        Holder<LotteryPool> pool = catalog.lotteries().stream().filter(value -> HolderHelper.id(value).equals(input.poolId())).findFirst()
                .orElseThrow(() -> new AgentException(AgentFailure.INVALID_INPUT));
        CommercePreviewOutput price = context.tool(BuiltinTools.COMMERCE_PREVIEW, "quote",
                new CommercePreviewInput(List.of(), List.of(HolderHelper.id(pool)), true, catalog.revision(), input.count())).getFirst();
        // Bounded batches replace the same detail card instead of appending per-prize messages.
        return SystemResults.data("system.result.lottery_preview", LotteryModule.summary(pool, price, catalog.revision(), input.offset()));
    }
}
