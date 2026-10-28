package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.output.QuestClaimOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.List;

/**
 * Claims one known quest reward reference and an optional selected choice.
 */
public final class QuestClaimModule extends BuiltinModule<QuestClaimInput> {
    public QuestClaimModule(ToolRegistry.Tool<QuestClaimInput, QuestClaimOutput> claim) {
        super(ResultTypes.CLAIM_ACTION, "Claim exactly one known FTB reward using a previously returned claimRef. Without a ref, list claimable quests then open system:quest_detail; never invent references.",
                QuestClaimInput.CODEC, List.of(claim), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<QuestClaimInput> routing() {
        return ModuleRouting.<QuestClaimInput>defaults()
                .semantic(RoutingProfile.hints(
                        List.of("领取这个任务的奖励", "把任务奖励给我", "选第二个任务奖励", "领取全部已完成任务奖励"),
                        List.of("Claim this quest reward", "Give me the quest rewards", "Choose the second quest reward", "Claim all completed quest rewards")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestClaimInput input) throws Exception {
        QuestClaimOutput result = context.tool(BuiltinTools.QUEST_CLAIM, "claim", input);
        return AgentResult.data(result.state().equals("CLAIM_EXECUTED") ? "system.result.claim" : "system.result.claim_unavailable", ResultTypes.CLAIM.of(result));
    }
}
