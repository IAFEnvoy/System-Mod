package com.iafenvoy.system.assistant.tool.jobs;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.jobs.OnlineJobs;
import com.iafenvoy.system.data.input.JobStartInput;
import com.iafenvoy.system.data.output.OnlineJobsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record JobStartTool(OnlineJobs jobs) implements ToolRegistry.Handler<JobStartInput, OnlineJobsOutput> {
    public ToolRegistry.Tool<JobStartInput, OnlineJobsOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.JOB_START, true, false, false, this);
    }

    @Override
    public CompletionStage<OnlineJobsOutput> execute(ExecutionContext context, JobStartInput input) {
        return CompletableFuture.completedFuture(this.jobs.start(context, input.job()));
    }
}
