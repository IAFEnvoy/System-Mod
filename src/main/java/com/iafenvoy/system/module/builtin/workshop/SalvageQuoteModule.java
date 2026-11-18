package com.iafenvoy.system.module.builtin.workshop;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.SalvageQuoteInput;
import com.iafenvoy.system.data.output.SalvageQuoteOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class SalvageQuoteModule extends AbstractAgentModule<SalvageQuoteInput> {
    public SalvageQuoteModule(ToolRegistry.Tool<SalvageQuoteInput, SalvageQuoteOutput> quote) {
        super(SalvageQuoteInput.CODEC, "Quote salvaging one main-hand item. Never destroys equipment without the player's confirmation button.", List.of(quote), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<SalvageQuoteInput> routing() {
        return ModuleRouting.<SalvageQuoteInput>defaults().button(_ -> SalvageQuoteInput.INSTANCE)
                .command(AgentCommand.literal(SalvageQuoteInput.INSTANCE, "salvage", "分解"))
                .semantic(RoutingProfile.read(_ -> SalvageQuoteInput.INSTANCE,
                                List.of("分解手上的武器", "回收主手装备", "看看这个装备能拆出什么"),
                                List.of("Salvage my held weapon", "Recycle my main-hand equipment", "What materials can I salvage from this?"))
                        .choice("system.action.salvage_quote"));
    }

    @Override
    public AgentResult execute(ExecutionContext context, SalvageQuoteInput input) throws Exception {
        return AgentResult.data("system.result.salvage_quote", context.tool(BuiltinTools.SALVAGE_QUOTE, "quote", input));
    }
}
