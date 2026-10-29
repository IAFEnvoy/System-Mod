package com.iafenvoy.system.agent.builtin.advancement;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.AdvancementInput;
import com.iafenvoy.system.agent.builtin.input.FeatureInput;
import com.iafenvoy.system.agent.builtin.input.RewardCreditInput;
import com.iafenvoy.system.agent.builtin.output.CreditReceiptOutput;
import com.iafenvoy.system.agent.builtin.output.FeatureOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.store.PlayerData;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Runs trusted advancement unlocks and reward credits through event-only tools.
 */
public final class AdvancementFollowupModule extends BuiltinModule<AdvancementInput> {
    public AdvancementFollowupModule(ToolRegistry.Tool<FeatureInput, FeatureOutput> unlock, ToolRegistry.Tool<RewardCreditInput, CreditReceiptOutput> credit) {
        super(AdvancementInput.CODEC, "Trusted advancement workflow; event entry only.", List.of(unlock, credit), false, true, ExecutionPolicy.event());
    }

    @Override
    public AgentResult execute(ExecutionContext context, AdvancementInput input) throws Exception {
        if (input.featureId().isPresent())
            context.tool(BuiltinTools.FEATURE_UNLOCK, "unlock", new FeatureInput(input.featureId().get()));
        if (input.rewardId().isPresent()) {
            ResourceLocation rewardId = input.rewardId().get();
            PlayerData.Reward reward = context.catalog().rewards().stream().filter(value -> value.id().equals(rewardId)).findFirst()
                    .orElseThrow(() -> new AgentException("INVALID_INPUT"));
            context.tool(BuiltinTools.REWARD_CREDIT, "milestone", new RewardCreditInput("event:" + context.taskId(), reward));
        }
        return AgentResult.ok("system.result.milestone");
    }
}
