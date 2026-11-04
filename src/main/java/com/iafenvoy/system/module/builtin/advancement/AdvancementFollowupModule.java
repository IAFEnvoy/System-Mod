package com.iafenvoy.system.module.builtin.advancement;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.data.input.AdvancementInput;
import com.iafenvoy.system.data.input.FeatureInput;
import com.iafenvoy.system.data.input.RewardCreditInput;
import com.iafenvoy.system.data.output.CreditReceiptOutput;
import com.iafenvoy.system.data.output.FeatureOutput;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.util.PersistHolder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Runs trusted advancement unlocks and reward credits through event-only tools.
 */
public final class AdvancementFollowupModule extends AbstractAgentModule<AdvancementInput> {
    private final CommerceCatalogs catalogs;

    public AdvancementFollowupModule(CommerceCatalogs catalogs, ToolRegistry.Tool<FeatureInput, FeatureOutput> unlock, ToolRegistry.Tool<RewardCreditInput, CreditReceiptOutput> credit) {
        super(AdvancementInput.CODEC, "Trusted advancement workflow; event entry only.", List.of(unlock, credit), false, true, ExecutionPolicy.EVENT);
        this.catalogs = catalogs;
    }

    @Override
    public AgentResult execute(ExecutionContext context, AdvancementInput input) throws Exception {
        if (input.featureId().isPresent())
            context.tool(BuiltinTools.FEATURE_UNLOCK, "unlock", new FeatureInput(input.featureId().get()));
        if (input.rewardId().isPresent()) {
            Identifier rewardId = input.rewardId().get();
            PersistHolder<Reward> reward = this.catalogs.all().rewards().stream().filter(value -> HolderHelper.id(value).equals(rewardId))
                    .map(value -> value.value().reward(rewardId)).findFirst()
                    .orElseThrow(() -> new AgentException("INVALID_INPUT"));
            context.tool(BuiltinTools.REWARD_CREDIT, "milestone", new RewardCreditInput("event:" + context.taskId(), reward));
        }
        return AgentResult.ok("system.result.milestone");
    }
}
