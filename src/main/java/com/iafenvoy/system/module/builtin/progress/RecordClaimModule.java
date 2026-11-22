package com.iafenvoy.system.module.builtin.progress;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.RecordClaimInput;
import com.iafenvoy.system.data.output.RecordRewardsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class RecordClaimModule extends AbstractAgentModule<RecordClaimInput> {
    public RecordClaimModule(ToolRegistry.Tool<RecordClaimInput, RecordRewardsOutput, ExecutionContext> tool) {
        super(RecordClaimInput.CODEC, "Claim an already earned progress reward to System Space, using an exact observed entitlement ID. Idempotent; does not withdraw contents.", List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<RecordClaimInput> routing() {
        return ModuleRouting.<RecordClaimInput>defaults().button(a -> new RecordClaimInput(a.required("entitlement")))
                .command(AgentCommand.argument("<entitlement>", RecordClaimInput::new, "record_claim", "领取进度奖励")).explicitOnly();
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, RecordClaimInput input) throws Exception {
        return SystemResults.data("system.records.transferred", context.tool(BuiltinTools.RECORD_CLAIM, "claim", input));
    }
}
