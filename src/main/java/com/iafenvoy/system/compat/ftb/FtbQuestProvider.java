package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.quest.QuestProvider;
import com.iafenvoy.system.assistant.quest.QuestSource;
import com.iafenvoy.system.data.input.*;
import com.iafenvoy.system.data.output.*;
import com.iafenvoy.system.speech.quest.QuestNarration;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ChoiceReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.*;

/**
 * Native FTB operations implement the common quest API; no FTB type escapes this adapter.
 */
final class FtbQuestProvider implements QuestProvider.Submission, QuestProvider.Rewards,
        QuestProvider.Guides, QuestProvider.StarterGifts, QuestProvider.Tracking, QuestProvider.Narration {
    static final QuestSource SOURCE = new QuestSource(Identifier.parse("system:ftb_quests"), Component.translatable("system.quest.source.ftb_quests"));
    private final FtbQuestAccess access;
    private final FtbQuestGuides guides;
    private final FtbQuestSpeech speech;

    FtbQuestProvider(FtbQuestAccess access) {
        this.access = access;
        this.guides = new FtbQuestGuides(access);
        this.speech = new FtbQuestSpeech(access);
    }

    @Override
    public QuestSource source() {
        return SOURCE;
    }

    @Override
    public CompletionStage<List<QuestBriefOutput>> query(ExecutionContext context, QuestQueryInput input) {
        return this.access.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE));
            FtbQuestReader reader = new FtbQuestReader(file, team);
            List<Quest> quests = switch (input.view()) {
                case ACTIVE -> reader.active(player);
                case CLAIMABLE -> reader.claimable(player);
                case SUBMITTABLE -> reader.submittable(player);
            };
            List<Quest> matches = quests.stream().filter(quest -> FtbQuestReader.matches(player, quest, input.query())).toList();
            return matches.stream().map(quest -> brief(player, team, quest, 0)).toList();
        });
    }

    @Override
    public CompletionStage<List<QuestBriefOutput>> find(ExecutionContext context, QuestFindInput input) {
        return this.access.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE));
            List<Quest> matches = new FtbQuestReader(file, team).find(player, input.query());
            return matches.stream().map(quest -> brief(player, team, quest, 0)).toList();
        });
    }

    @Override
    public CompletionStage<QuestOutput> detail(ExecutionContext context, QuestDetailInput input, Optional<String> revision) {
        if (!input.query().isBlank()) throw new AgentException(AgentFailure.INVALID_INPUT);
        String questId = input.questId();
        return this.access.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE));
            Quest selected = selected(file, team, questId, revision);
            if (selected == null || !FtbQuestReader.detailAvailable(team, selected))
                throw new AgentException(AgentFailure.QUEST_UNAVAILABLE);
            return FtbQuestAccess.detail(player, team, selected, input, this.access.references);
        });
    }

    @Override
    public CompletionStage<QuestSubmitOutput> submit(ExecutionContext context, QuestSubmitInput input, Optional<String> revision) {
        return this.access.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.NOT_AUTHORIZED));
            Quest quest = selected(file, team, input.questId(), revision);
            if (quest == null || !FtbQuestReader.active(team, quest) || team.isProgressionPaused(player))
                throw new AgentException(AgentFailure.QUEST_UNAVAILABLE);
            if (input.checkmarkOnly() && !FtbQuestReader.canComplete(player, team, quest))
                throw new AgentException(AgentFailure.QUEST_UNAVAILABLE);
            List<Task> tasks = FtbQuestReader.submittableTasks(quest).stream()
                    .filter(task -> !input.checkmarkOnly() || task.getClass() == CheckmarkTask.class).toList();
            if (tasks.isEmpty()) throw new AgentException(AgentFailure.NO_SUBMITTABLE_TASK);
            List<Long> before = tasks.stream().map(team::getProgress).toList();
            int completions = team.getCompletionCount(quest);
            file.withPlayerContext(player, () -> {
                for (Task task : tasks) {
                    // Auto-claimed repeatable quests may reset immediately; one command never enters the next cycle.
                    if (team.isCompleted(quest) || team.getCompletionCount(quest) != completions || team.isLocked()
                            || !team.canStartTasks(quest) || team.isProgressionPaused(player)) break;
                    if (FtbQuestReader.pendingSubmission(team, task)) task.submitTask(team, player);
                }
            });
            boolean changed = !before.equals(tasks.stream().map(team::getProgress).toList()) || team.getCompletionCount(quest) != completions;
            boolean completed = team.isCompleted(quest) || team.getCompletionCount(quest) != completions;
            if (completed) this.access.notifications.submitted(player, file, team, quest);
            return new QuestSubmitOutput(key(quest.getId()), FtbQuestText.title(player, quest), tasks.stream()
                    .map(task -> task(player, team, task)).toList(), changed, completed, SOURCE);
        });
    }

    @Override
    public CompletionStage<QuestClaimOutput> claim(ExecutionContext context, QuestClaimInput input) {
        RewardReference reference = this.access.references.get(input.claimRef());
        if (reference == null || !reference.player().equals(context.player()) || reference.expires() < System.currentTimeMillis())
            throw new AgentException(AgentFailure.STALE_REFERENCE);
        long questId = reference.quest();
        long rewardId = reference.reward();
        int choice = input.choice().orElse(-1);
        return this.access.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance();
            Quest quest = file.getQuest(questId);
            Reward reward = file.getReward(rewardId);
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.NOT_AUTHORIZED));
            if (quest == null || reward == null || !team.getTeamId().equals(reference.team())
                    || quest != reference.questVersion() || reward != reference.rewardVersion() || choiceVersion(reward) != reference.choiceVersion()
                    || team.getCompletedTime(questId).map(Date::getTime).orElse(0L) != reference.completedAt())
                throw new AgentException(AgentFailure.STALE_REFERENCE);
            if (reward.getQuest() != quest || !FtbQuestReader.readable(team, quest) || team.isLocked()
                    || !team.isCompleted(quest) || team.isRewardBlocked(reward) || reward.getAutoClaimType() == RewardAutoClaim.INVISIBLE)
                throw new AgentException(AgentFailure.NOT_AUTHORIZED);
            if (!"CAN_CLAIM".equals(team.getClaimType(player.getUUID(), reward).name()))
                return new QuestClaimOutput("ALREADY_CLAIMED", Optional.of(key(questId)), Optional.of(hex(rewardId)), Optional.empty());
            if (reward instanceof ChoiceReward selectable) {
                if (selectable.getTable() == null || choice < 0 || choice >= selectable.getTable().getWeightedRewards().size())
                    throw new AgentException(AgentFailure.CHOICE_REQUIRED);
                Reward selected = selectable.getTable().getWeightedRewards().get(choice).getReward();
                if (selected instanceof ChoiceReward) throw new AgentException(AgentFailure.UNSUPPORTED);
                // Match the native 26.1 choice handler: reserve the outer reward before granting its selection.
                if (team.markRewardAsClaimed(player.getUUID(), reward, System.currentTimeMillis()))
                    selected.claim(player, true);
            } else {
                if (choice != -1) throw new AgentException(AgentFailure.INVALID_INPUT);
                team.claimReward(player, reward, true);
            }
            if (!team.isRewardClaimed(player.getUUID(), reward)) throw new AgentException(AgentFailure.CLAIM_REJECTED);
            return new QuestClaimOutput("CLAIM_EXECUTED", Optional.of(key(questId)), Optional.of(hex(rewardId)), Optional.of(false));
        });
    }

    @Override
    public CompletionStage<StarterGiftOutput> gifts(ExecutionContext context) {
        return this.access.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.NOT_AUTHORIZED));
            return this.access.gifts.preview(player, file, team);
        });
    }

    @Override
    public CompletionStage<StarterGiftOutput> claimGifts(ExecutionContext context, StarterGiftInput input) {
        return this.access.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.NOT_AUTHORIZED));
            return this.access.gifts.claim(player, file, team, input.claimRefs());
        });
    }

    @Override
    public CompletionStage<QuestGuideOutput> guides(ExecutionContext context, QuestGuideInput input) {
        return this.guides.search(context, input);
    }

    @Override
    public Optional<QuestPinOutput> tracking(ServerPlayer player, String localId) {
        return FtbQuestDisplay.read(player, localId);
    }

    @Override
    public CompletionStage<QuestNarration.Quest> load(ExecutionContext context, String questId) {
        return this.speech.load(context, questId);
    }
}
