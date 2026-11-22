package com.iafenvoy.system.module.builtin.workshop;

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
import com.iafenvoy.system.data.input.RepairQuoteInput;
import com.iafenvoy.system.data.output.RepairQuoteOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class RepairQuoteModule extends AbstractAgentModule<RepairQuoteInput> {
    public RepairQuoteModule(ToolRegistry.Tool<RepairQuoteInput, RepairQuoteOutput, ExecutionContext> quote) {
        super(RepairQuoteInput.CODEC, "Quote a full main-hand item repair. Payment requires the returned confirmation button.", List.of(quote), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<RepairQuoteInput> routing() {
        return ModuleRouting.<RepairQuoteInput>defaults().button(_ -> RepairQuoteInput.INSTANCE)
                .command(AgentCommand.literal(RepairQuoteInput.INSTANCE, "repair", "维修"))
                .semantic(RoutingProfile.read(_ -> RepairQuoteInput.INSTANCE,
                                List.of("维修手中的装备", "帮我修一下主手工具", "看看维修这个装备需要什么"),
                                List.of("Repair my held equipment", "Fix my main-hand tool", "How much does repairing this cost?"))
                        .choice("system.action.repair_quote"));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, RepairQuoteInput input) throws Exception {
        return SystemResults.data("system.result.repair_quote", context.tool(BuiltinTools.REPAIR_QUOTE, "quote", input));
    }
}
