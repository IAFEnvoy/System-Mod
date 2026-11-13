package com.iafenvoy.system.assistant.tool.jobs;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.jobs.OnlineJobs;
import com.iafenvoy.system.data.input.JobClaimInput;
import com.iafenvoy.system.data.output.RewardOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public record JobClaimTool(OnlineJobs jobs) implements ToolRegistry.Handler<JobClaimInput, RewardOutput> {
    public ToolRegistry.Tool<JobClaimInput, RewardOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.JOB_CLAIM, true, true, false, this);
    }

    @Override
    public CompletionStage<RewardOutput> execute(ExecutionContext context, JobClaimInput input) {
        return CompletableFuture.completedFuture(this.jobs.claim(context, input.run()));
    }
}
