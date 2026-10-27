package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.output.QuestClaimOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ChoiceReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.*;

/**
 * One FTB operation, loaded only after the optional dependency check.
 */
final class FtbQuestClaimTool implements ToolRegistry.Handler<QuestClaimInput, QuestClaimOutput> {
    private final FtbQuestAccess access;

    FtbQuestClaimTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<QuestClaimInput, QuestClaimOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_CLAIM, true, false, false, this);
    }

    @Override
    public CompletionStage<QuestClaimOutput> execute(ExecutionContext context, QuestClaimInput input) {
        RewardReference reference = this.access.references.get(input.claimRef());
        if (reference == null || !reference.player().equals(context.player()) || reference.expires() < System.currentTimeMillis())
            throw new AgentException("STALE_REFERENCE");
        long questId = reference.quest();
        long rewardId = reference.reward();
        int choice = input.choice().orElse(-1);
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            Quest quest = file.getQuest(questId);
            Reward reward = file.getReward(rewardId);
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("NOT_AUTHORIZED"));
            if (quest == null || reward == null || !team.getTeamId().equals(reference.team())
                    || quest != reference.questVersion() || reward != reference.rewardVersion() || choiceVersion(reward) != reference.choiceVersion()
                    || team.getCompletedTime(questId).map(Date::getTime).orElse(0L) != reference.completedAt())
                throw new AgentException("STALE_REFERENCE");
            if (reward.getQuest() != quest || !FtbQuestReader.readable(team, quest) || team.isLocked()
                    || !team.isCompleted(quest) || team.isRewardBlocked(reward) || reward.getAutoClaimType() == RewardAutoClaim.INVISIBLE)
                throw new AgentException("NOT_AUTHORIZED");
            if (!"CAN_CLAIM".equals(team.getClaimType(player.getUUID(), reward).name()))
                return new QuestClaimOutput("ALREADY_CLAIMED", Optional.empty(), Optional.empty(), Optional.empty());
            if (reward instanceof ChoiceReward selectable) {
                if (selectable.getTable() == null || choice < 0 || choice >= selectable.getTable().getWeightedRewards().size())
                    throw new AgentException("CHOICE_REQUIRED");
                Reward selected = selectable.getTable().getWeightedRewards().get(choice).getReward();
                if (selected instanceof ChoiceReward) throw new AgentException("UNSUPPORTED");
                if (!team.markRewardAsClaimed(player.getUUID(), reward, System.currentTimeMillis()))
                    throw new AgentException("NOT_AUTHORIZED");
                selected.claim(player, true);
            } else {
                if (choice != -1) throw new AgentException("INVALID_INPUT");
                if (!team.markRewardAsClaimed(player.getUUID(), reward, System.currentTimeMillis()))
                    throw new AgentException("NOT_AUTHORIZED");
                reward.claim(player, true);
            }
            return new QuestClaimOutput("CLAIM_EXECUTED", Optional.of(hex(questId)), Optional.of(hex(rewardId)), Optional.of(false));
        });
    }
}
