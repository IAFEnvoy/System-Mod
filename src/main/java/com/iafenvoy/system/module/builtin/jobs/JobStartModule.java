package com.iafenvoy.system.module.builtin.jobs;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.JobStartInput;
import com.iafenvoy.system.data.output.OnlineJobsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class JobStartModule extends AbstractAgentModule<JobStartInput> {
    public JobStartModule(ToolRegistry.Tool<JobStartInput, OnlineJobsOutput> start) {
        super(JobStartInput.CODEC, "Start one online job selected by the player.", List.of(start), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<JobStartInput> routing() {
        return ModuleRouting.<JobStartInput>defaults().button(a -> new JobStartInput(a.identifier("job")))
                .command(AgentCommand.identifier("job", JobStartInput::new, "work", "开工"));
    }

    @Override
    public AgentResult execute(ExecutionContext context, JobStartInput input) throws Exception {
        return AgentResult.data("system.result.job_started", context.tool(BuiltinTools.JOB_START, "start", input));
    }
}
