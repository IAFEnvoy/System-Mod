package com.iafenvoy.system.assistant.tool.jobs;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.jobs.OnlineJobs;
import com.iafenvoy.system.data.input.JobCancelInput;
import com.iafenvoy.system.data.output.OnlineJobsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record JobCancelTool(OnlineJobs jobs) implements ToolRegistry.Handler<JobCancelInput, OnlineJobsOutput> {
    public ToolRegistry.Tool<JobCancelInput, OnlineJobsOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.JOB_CANCEL, true, false, false, this);
    }

    @Override
    public CompletionStage<OnlineJobsOutput> execute(ExecutionContext context, JobCancelInput input) {
        return CompletableFuture.completedFuture(this.jobs.cancel(context, input.run()));
    }
}
