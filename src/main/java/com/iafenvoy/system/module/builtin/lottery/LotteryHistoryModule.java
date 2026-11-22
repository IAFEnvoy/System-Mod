package com.iafenvoy.system.module.builtin.lottery;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.command.AssistantCommandSource;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.LotteryHistoryInput;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public final class LotteryHistoryModule extends AbstractAgentModule<LotteryHistoryInput> {
    private final CommerceTransactions commerce;

    public LotteryHistoryModule(CommerceTransactions commerce) {
        super(LotteryHistoryInput.CODEC, "Read only my own lottery history, optionally filtered by an observed pool ID. Never execute a draw.", List.of(), true, false, ExecutionPolicy.quick(0, 0));
        this.commerce = commerce;
    }

    @Override
    public ModuleRouting<LotteryHistoryInput> routing() {
        return ModuleRouting.<LotteryHistoryInput>defaults()
                .button(a -> new LotteryHistoryInput(a.optional("pool", "pool").map(Identifier::parse), a.number(0, "offset"), a.optional("order", "order")))
                .command(new AgentCommand(List.of("lotteryhistory", "抽奖记录"), name -> LiteralArgumentBuilder.<AssistantCommandSource>literal(name)
                        .executes(command -> command.getSource().action(new LotteryHistoryInput(Optional.empty(), 0)))
                        .then(RequiredArgumentBuilder.<AssistantCommandSource, Identifier>argument("pool", IdentifierArgument.id())
                                .executes(command -> command.getSource().action(new LotteryHistoryInput(Optional.of(command.getArgument("pool", Identifier.class)), 0))))))
                .semantic(RoutingProfile.read(_ -> new LotteryHistoryInput(Optional.empty(), 0),
                        List.of("查看抽奖记录", "我的抽奖历史", "我之前抽到了什么"), List.of("Show my lottery history", "My previous lottery results")))
                .shortcut("system.lottery.history", new LotteryHistoryInput(Optional.empty(), 0));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, LotteryHistoryInput input) {
        return SystemResults.data("system.lottery.history", this.commerce.history(context, input));
    }
}
