package com.iafenvoy.system.module.builtin.jobs;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.JobClaimInput;
import com.iafenvoy.system.data.output.RewardOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class JobClaimModule extends AbstractAgentModule<JobClaimInput> {
    public JobClaimModule(ToolRegistry.Tool<JobClaimInput, RewardOutput, ExecutionContext> claim) {
        super(JobClaimInput.CODEC, "Credit a completed online job to the inbox exactly once.", List.of(claim), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<JobClaimInput> routing() {
        return ModuleRouting.<JobClaimInput>defaults().button(a -> new JobClaimInput(a.required("run"))).structuredOnly();
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, JobClaimInput input) throws Exception {
        return SystemResults.data("system.result.job_claimed", context.tool(BuiltinTools.JOB_CLAIM, "claim", input));
    }
}
