package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gift operations run on the server thread; concurrent references also allow immediate revoke cleanup.
 */
final class FtbStarterGifts {
    private final Map<String, Reference> references = new ConcurrentHashMap<>();

    void clear(UUID player) {
        this.references.values().removeIf(reference -> reference.player().equals(player));
    }

    private static List<Reward> rewards(TeamData team, Quest quest) {
        return quest.getRewards().stream().filter(reward -> !team.isRewardBlocked(reward)
                && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE).toList();
    }

    StarterGiftOutput preview(ServerPlayer player, ServerQuestFile file, TeamData team) {
        return new StarterGiftOutput(new FtbQuestReader(file, team).availableStarterGifts(player).stream()
                .map(quest -> this.previewGift(player, team, quest)).toList());
    }

    private StarterGiftOutput.Gift previewGift(ServerPlayer player, TeamData team, Quest quest) {
        Optional<String> reference = Optional.empty();
        if (FtbQuestReader.starterGiftAvailable(player, team, quest)) {
            long now = System.currentTimeMillis();
            this.references.values().removeIf(value -> value.expires() < now);
            // Refreshing details renews the same reference instead of accumulating duplicate gift tokens.
            String id = this.references.entrySet().stream().filter(entry -> entry.getValue().player().equals(player.getUUID())
                            && entry.getValue().team().equals(team.getTeamId()) && entry.getValue().quest() == quest)
                    .map(Map.Entry::getKey).findFirst().orElseGet(() -> UUID.randomUUID().toString());
            this.references.put(id, new Reference(player.getUUID(), team.getTeamId(), quest,
                    List.copyOf(quest.getTasksAsList()), List.copyOf(quest.getRewards()),
                    team.getCompletedTime(quest.getId()).map(Date::getTime).orElse(0L), now + 120000));
            reference = Optional.of(id);
        }
        return new StarterGiftOutput.Gift(FtbQuestAccess.hex(quest.getId()), rewards(team, quest).stream()
                .filter(reward -> !team.isRewardClaimed(player.getUUID(), reward))
                .flatMap(reward -> FtbQuestAccess.previewItems(reward).stream()).toList(), reference, Optional.empty());
    }

    StarterGiftOutput claim(ServerPlayer player, ServerQuestFile file, TeamData team, List<String> ids) {
        if (ids.isEmpty()) throw new AgentException("INVALID_INPUT");
        // Validate ownership before any write, then retain per-gift failures without replaying successful claims.
        Map<String, Reference> requested = new LinkedHashMap<>();
        for (String id : ids) {
            Reference reference = this.references.get(id);
            if (reference == null || !reference.player().equals(player.getUUID()))
                throw new AgentException("STALE_REFERENCE");
            requested.put(id, reference);
        }
        Map<String, StarterGiftOutput.Gift> failures = new LinkedHashMap<>();
        for (Map.Entry<String, Reference> entry : requested.entrySet()) {
            Reference reference = entry.getValue();
            try {
                this.claimOne(player, file, team, entry.getKey());
            } catch (RuntimeException error) {
                Quest quest = reference.quest();
                if (file.getQuest(quest.getId()) == quest && FtbQuestReader.readable(team, quest))
                    failures.put(FtbQuestAccess.hex(quest.getId()), new StarterGiftOutput.Gift(FtbQuestAccess.hex(quest.getId()),
                            rewards(team, quest).stream().flatMap(reward -> FtbQuestAccess.previewItems(reward).stream()).toList(),
                            Optional.empty(), Optional.of(AgentEngine.errorCode(error))));
            }
        }
        StarterGiftOutput remaining = this.preview(player, file, team);
        List<StarterGiftOutput.Gift> gifts = new ArrayList<>();
        for (StarterGiftOutput.Gift gift : remaining.gifts()) {
            StarterGiftOutput.Gift failure = failures.remove(gift.questId());
            gifts.add(failure == null ? gift : new StarterGiftOutput.Gift(gift.questId(), gift.items(), gift.claimRef(), failure.error()));
        }
        gifts.addAll(failures.values());
        return new StarterGiftOutput(gifts);
    }

    private void claimOne(ServerPlayer player, ServerQuestFile file, TeamData team, String id) {
        Reference reference = this.references.get(id);
        if (reference == null || !reference.player().equals(player.getUUID()) || reference.expires() < System.currentTimeMillis())
            throw new AgentException("STALE_REFERENCE");
        Quest quest = reference.quest();
        long completed = team.getCompletedTime(quest.getId()).map(Date::getTime).orElse(0L);
        if (!reference.team().equals(team.getTeamId()) || file.getQuest(quest.getId()) != quest
                || !reference.tasks().equals(quest.getTasksAsList()) || !reference.rewards().equals(quest.getRewards())
                || reference.completedAt() != 0 && reference.completedAt() != completed)
            throw new AgentException("STALE_REFERENCE");
        if (FtbQuestReader.kind(quest) != FtbQuestReader.Kind.STARTER_GIFT || !FtbQuestReader.readable(team, quest)
                || team.isLocked() || !team.isCompleted(quest) && (!team.canStartTasks(quest) || team.isProgressionPaused(player)))
            throw new AgentException("NOT_AUTHORIZED");
        List<Reward> rewards = rewards(team, quest);
        if (rewards.isEmpty()) throw new AgentException("NOT_AUTHORIZED");
        if (rewards.stream().allMatch(reward -> team.isRewardClaimed(player.getUUID(), reward)))
            return;
        // Native submission honors sequential tasks, progression pause, dependency events and auto-claims.
        if (!team.isCompleted(quest)) file.withPlayerContext(player, () -> {
            for (Task task : reference.tasks()) if (!team.isCompleted(task)) task.submitTask(team, player);
        });
        if (!team.isCompleted(quest)) throw new AgentException("QUEST_UNAVAILABLE");
        for (Reward reward : rewards)
            if (FtbQuestReader.canClaim(player, team, reward)) team.claimReward(player, reward, true);
        // Completion hooks can change reward blocking; never report a complete claim for a partial result.
        if (rewards.stream().anyMatch(reward -> !team.isRewardClaimed(player.getUUID(), reward)))
            throw new AgentException("EFFECT_UNKNOWN");
    }

    private record Reference(UUID player, UUID team, Quest quest, List<Task> tasks, List<Reward> rewards,
                             long completedAt, long expires) {
    }
}
