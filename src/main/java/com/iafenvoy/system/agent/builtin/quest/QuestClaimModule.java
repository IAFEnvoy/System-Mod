package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.QuestClaimOutput;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.card.CardUpdate;

import java.util.List;

/**
 * Claims one known quest reward reference and an optional selected choice.
 */
public final class QuestClaimModule extends BuiltinModule<QuestClaimInput> {
    public QuestClaimModule(ToolRegistry.Tool<QuestClaimInput, QuestClaimOutput> claim, ToolRegistry.Tool<QuestDetailInput, QuestOutput> detail) {
        super(QuestClaimInput.CODEC, "Claim exactly one known FTB reward using a previously returned claimRef. Without a ref, list claimable quests then open system:quest_detail; never invent references.", List.of(claim, detail), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<QuestClaimInput> routing() {
        return ModuleRouting.<QuestClaimInput>defaults()
                .button(a -> new QuestClaimInput(a.required("claim_ref", "claimRef"), a.optional("choice").map(Integer::parseInt)))
                .semantic(RoutingProfile.hints(
                        List.of("领取这个任务的奖励", "把任务奖励给我", "选第二个任务奖励", "领取全部已完成任务奖励"),
                        List.of("Claim this quest reward", "Give me the quest rewards", "Choose the second quest reward", "Claim all completed quest rewards")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestClaimInput input) throws Exception {
        QuestClaimOutput result = context.tool(BuiltinTools.QUEST_CLAIM, "claim", input);
        if (result.questId().isEmpty()) return AgentResult.data("system.result.claim_unavailable", result);
        QuestOutput updated = context.tool(BuiltinTools.QUEST_DETAIL, "refresh", QuestDetailInput.byId(result.questId().get()));
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.claim", "",
                updated.rewardPending()
                        ? List.of(CardUpdate.show(updated)) : List.of(), true);
    }
}
