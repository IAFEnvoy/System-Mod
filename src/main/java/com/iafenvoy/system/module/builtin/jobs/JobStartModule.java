package com.iafenvoy.system.module.builtin.jobs;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.JobStartInput;
import com.iafenvoy.system.data.output.OnlineJobsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class JobStartModule extends AbstractAgentModule<JobStartInput> {
    public JobStartModule(ToolRegistry.Tool<JobStartInput, OnlineJobsOutput, ExecutionContext> start) {
        super(JobStartInput.CODEC, "Preview one online job and its frozen material costs. Only the player's confirmation can pay and enqueue it; never confirm on their behalf.", List.of(start), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<JobStartInput> routing() {
        return ModuleRouting.<JobStartInput>defaults().button(a -> new JobStartInput(a.identifier("job"), a.optional("quote")))
                .command(AgentCommand.identifier("job", JobStartInput::new, "work", "开工"));
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, JobStartInput input) throws Exception {
        return SystemResults.data(input.quote().isPresent() ? "system.result.job_started" : "system.jobs.confirm_start", context.tool(BuiltinTools.JOB_START, "start", input));
    }
}
