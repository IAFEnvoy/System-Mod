package com.iafenvoy.system.agent.builtin.advancement;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.FeatureInput;
import com.iafenvoy.system.agent.builtin.output.FeatureOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.advancement.AdvancementSystemModule;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Unlocks a validated feature ID for a trusted event workflow.
 */
public final class FeatureUnlockTool implements ToolRegistry.Handler<FeatureInput, FeatureOutput> {
    public ToolRegistry.Tool<FeatureInput, FeatureOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.FEATURE_UNLOCK, true, true, true, this);
    }

    @Override
    public CompletionStage<FeatureOutput> execute(ExecutionContext context, FeatureInput input) {
        String id = input.featureId();
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new AgentException("INVALID_INPUT");
        return CompletableFuture.completedFuture(context.store().update(context.player(), data -> {
            ModuleData.get(data, AdvancementSystemModule.STORAGE).unlocked.add(id);
            return new FeatureOutput(id, true);
        }));
    }
}
