package com.iafenvoy.system.module.builtin.jobs;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.JobCancelInput;
import com.iafenvoy.system.data.output.OnlineJobsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class JobCancelModule extends AbstractAgentModule<JobCancelInput> {
    public JobCancelModule(ToolRegistry.Tool<JobCancelInput, OnlineJobsOutput, ExecutionContext> cancel) {
        super(JobCancelInput.CODEC, "Cancel the selected unfinished online job, discarding its progress.", List.of(cancel), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<JobCancelInput> routing() {
        return ModuleRouting.<JobCancelInput>defaults().button(a -> new JobCancelInput(a.required("run"))).structuredOnly();
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, JobCancelInput input) throws Exception {
        return SystemResults.data("system.result.job_cancelled", context.tool(BuiltinTools.JOB_CANCEL, "cancel", input));
    }
}
