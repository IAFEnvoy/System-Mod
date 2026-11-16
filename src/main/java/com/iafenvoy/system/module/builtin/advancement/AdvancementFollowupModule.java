package com.iafenvoy.system.module.builtin.advancement;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.AdvancementInput;
import com.iafenvoy.system.data.input.FeatureInput;
import com.iafenvoy.system.data.input.RewardCreditInput;
import com.iafenvoy.system.data.output.CreditReceiptOutput;
import com.iafenvoy.system.data.output.FeatureOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

/**
 * Runs trusted advancement unlocks and reward credits through event-only tools.
 */
public final class AdvancementFollowupModule extends AbstractAgentModule<AdvancementInput> {
    public AdvancementFollowupModule(ToolRegistry.Tool<FeatureInput, FeatureOutput> unlock, ToolRegistry.Tool<RewardCreditInput, CreditReceiptOutput> credit) {
        super(AdvancementInput.CODEC, "Trusted advancement workflow; event entry only.", List.of(unlock, credit), false, true, ExecutionPolicy.EVENT);
    }

    @Override
    public AgentResult execute(ExecutionContext context, AdvancementInput input) throws Exception {
        if (input.featureId().isPresent())
            context.tool(BuiltinTools.FEATURE_UNLOCK, "unlock", new FeatureInput(input.featureId().get()));
        if (input.rewardId().isPresent())
            context.tool(BuiltinTools.REWARD_CREDIT, "milestone", new RewardCreditInput("event:" + context.taskId(), input.rewardId().get()));
        return AgentResult.ok("system.result.milestone");
    }
}
