package com.iafenvoy.system.module.builtin.workshop;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.assistant.workshop.Salvage;
import com.iafenvoy.system.data.input.SalvageHistoryInput;

import java.util.List;

public final class SalvageHistoryModule extends AbstractAgentModule<SalvageHistoryInput> {
    private final Salvage salvage;

    public SalvageHistoryModule(Salvage salvage) {
        super(SalvageHistoryInput.CODEC, "Read my salvage orders, ten per page.", List.of(), true, false, ExecutionPolicy.quick(0, 0));
        this.salvage = salvage;
    }

    @Override
    public ModuleRouting<SalvageHistoryInput> routing() {
        return ModuleRouting.<SalvageHistoryInput>defaults().button(args -> new SalvageHistoryInput(args.number(0, "offset")))
                .command(AgentCommand.literal(new SalvageHistoryInput(0), "salvage_history", "分解记录"));
    }

    @Override
    public AgentResult execute(ExecutionContext context, SalvageHistoryInput input) {
        return AgentResult.data("system.salvage.history", this.salvage.history(context, input.offset()));
    }
}
