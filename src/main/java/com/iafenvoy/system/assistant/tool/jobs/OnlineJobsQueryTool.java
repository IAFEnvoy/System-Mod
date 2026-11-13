package com.iafenvoy.system.assistant.tool.jobs;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.jobs.OnlineJobs;
import com.iafenvoy.system.data.input.OnlineJobsInput;
import com.iafenvoy.system.data.output.OnlineJobsOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record OnlineJobsQueryTool(OnlineJobs jobs) implements ToolRegistry.Handler<OnlineJobsInput, OnlineJobsOutput> {
    public ToolRegistry.Tool<OnlineJobsInput, OnlineJobsOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.ONLINE_JOBS, false, true, false, this)
                .forReasoning("List available online jobs and the player's current production progress. This does not start or claim a job.");
    }

    @Override
    public CompletionStage<OnlineJobsOutput> execute(ExecutionContext context, OnlineJobsInput input) {
        return CompletableFuture.completedFuture(this.jobs.query(context, input.offset()));
    }
}
