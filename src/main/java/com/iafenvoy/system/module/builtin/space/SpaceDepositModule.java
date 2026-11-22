package com.iafenvoy.system.module.builtin.space;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.routing.RoutingProfile;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.SpaceDepositInput;
import com.iafenvoy.system.data.output.SpaceInventoryOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class SpaceDepositModule extends AbstractAgentModule<SpaceDepositInput> {
    public SpaceDepositModule(ToolRegistry.Tool<SpaceDepositInput, SpaceInventoryOutput, ExecutionContext> tool) {
        super(SpaceDepositInput.CODEC, "Deposit items from the current main hand or a server-issued inventory reference. Never invent references or item contents.",
                List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<SpaceDepositInput> routing() {
        return ModuleRouting.<SpaceDepositInput>defaults()
                .button(a -> new SpaceDepositInput(a.required("reference"), a.number(1, "amount"), a.flag("stack")))
                .command(AgentCommand.argument("[amount or stack]", SpaceDepositInput::command, "deposit", "存入", "存放"))
                .explicit(SpaceDepositInput::parse)
                .semantic(RoutingProfile.hints(List.of("存入手持物品", "存入一组手持物品", "存入十个手持物品"),
                        List.of("Deposit my held item", "Deposit a stack of the item in my main hand")));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, SpaceDepositInput input) throws Exception {
        AgentResult<CardUpdate> result = SystemResults.data("system.space.deposit_title", context.tool(BuiltinTools.SPACE_DEPOSIT, "deposit", input));
        return result.withPresentation(result.messageKey(), result.text(), result.cards(), !input.reference().isBlank());
    }
}
