package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import com.iafenvoy.system.agent.builtin.output.QuestPageOutput;
import com.iafenvoy.system.agent.builtin.output.QuestRewardOutput;
import com.iafenvoy.system.agent.builtin.output.QuestTaskOutput;
import com.iafenvoy.system.agent.builtin.output.SectionPageOutput;
import com.iafenvoy.system.runtime.server.MinecraftTools;
import com.iafenvoy.system.util.Json;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.loot.WeightedReward;
import dev.ftb.mods.ftbquests.quest.reward.ChoiceReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/** Shared quest pages and reward references; operations are separate single-purpose tools. */
final class FtbQuestAccess {
    static final int PAGE_SIZE = 8;
    final MinecraftTools game;
    final Map<String, RewardReference> references = new ConcurrentHashMap<>();
    final Map<UUID, QuestPage> pages = new ConcurrentHashMap<>();

    FtbQuestAccess(AgentEngine engine, MinecraftTools game) {
        this.game = game;
        engine.onRevoke(player -> {
            this.references.values().removeIf(reference -> reference.player.equals(player));
            this.pages.remove(player);
        });
    }

    static boolean matches(Quest quest, String query) {
        return query.isBlank() || quest.getTitle().getString().toLowerCase(Locale.ROOT).contains(query)
                || hex(quest.getId()).equalsIgnoreCase(query);
    }

    static boolean canClaim(ServerPlayer player, TeamData team, Reward reward) {
        return !team.isLocked() && team.isCompleted(reward.getQuest()) && !team.isRewardBlocked(reward)
                && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE
                && "CAN_CLAIM".equals(team.getClaimType(player.getUUID(), reward).name());
    }

    static boolean hasClaim(ServerPlayer player, TeamData team, Quest quest) {
        return quest.getRewards().stream().anyMatch(reward -> canClaim(player, team, reward));
    }

    static QuestPageOutput page(ExecutionContext context, TeamData team, List<Quest> matches, int offset, Map<UUID, QuestPage> pages) {
        List<Quest> selected = matches.stream().skip(offset).limit(PAGE_SIZE).toList();
        List<QuestBriefOutput> quests = new ArrayList<>();
        for (Quest quest : selected)
            quests.add(new QuestBriefOutput(quests.size() + 1, hex(quest.getId()), Json.clip(quest.getTitle().getString(), 256),
                    Json.clip(quest.getChapter().getTitle().getString(), 128), team.getRelativeProgress(quest)));
        long now = System.currentTimeMillis();
        pages.values().removeIf(value -> value.expires < now);
        if (!pages.containsKey(context.player()) && pages.size() >= 4096) throw new AgentException("BUSY");
        // Indices refer to the last displayed page, never to a newly filtered or reordered quest file.
        pages.put(context.player(), new QuestPage(team.getTeamId(), context.incarnation(), context.memoryEpoch(), selected, now + 600000));
        return new QuestPageOutput(quests, matches.size() > offset + selected.size(), offset + selected.size());
    }

    static QuestOutput detail(ServerPlayer player, TeamData team, Quest quest, QuestDetailInput input, Map<String, RewardReference> references) {
        int rewardOffset = input.rewardOffset();
        int choiceOffset = input.choiceOffset();
        int taskOffset = input.taskOffset();
        int descriptionOffset = input.descriptionOffset();
        long now = System.currentTimeMillis();
        references.values().removeIf(value -> value.expires < now);
        long owned = references.values().stream().filter(ref -> ref.player.equals(player.getUUID())).count();
        List<QuestRewardOutput> rewards = new ArrayList<>();
        int visibleRewards = 0;
        for (Reward reward : quest.getRewards()) {
            if (team.isRewardBlocked(reward) || reward.getAutoClaimType() == RewardAutoClaim.INVISIBLE) continue;
            if (visibleRewards++ < rewardOffset || rewards.size() == 4) continue;
            boolean claimable = canClaim(player, team, reward);
            Optional<List<String>> choices = Optional.empty();
            boolean hasMoreChoices = false;
            if (reward instanceof ChoiceReward choice && choice.getTable() != null) {
                choices = Optional.of(choice.getTable().getWeightedRewards().stream().skip(choiceOffset).limit(8)
                        .map(candidate -> Json.clip(candidate.getReward().getTitle().getString(), 256)).toList());
                hasMoreChoices = choice.getTable().getWeightedRewards().size() > choiceOffset + choices.get().size();
            }
            Optional<String> claimRef = Optional.empty();
            // Only expanded details mint short-lived references, bound to the original player, team and objects.
            if (claimable && owned < 256 && references.size() < 4096) {
                String reference = UUID.randomUUID().toString();
                references.put(reference, new RewardReference(player.getUUID(), team.getTeamId(), quest.getId(), reward.getId(),
                        team.getCompletedTime(quest.getId()).map(Date::getTime).orElse(0L), quest, reward, choiceVersion(reward), now + 120000));
                claimRef = Optional.of(reference);
                owned++;
            } else if (claimable) claimable = false;
            rewards.add(new QuestRewardOutput(hex(reward.getId()), Json.clip(reward.getTitle().getString(), 256), claimable,
                    team.getClaimType(player.getUUID(), reward).name(), choices, choices.isPresent() ? choiceOffset : 0, hasMoreChoices, claimRef));
        }
        List<QuestTaskOutput> tasks = quest.getTasksAsList().stream().skip(taskOffset).limit(12)
                .map(task -> new QuestTaskOutput(Json.clip(task.getTitle().getString(), 256), team.getProgress(task), task.getMaxProgress())).toList();
        String description = String.join("\n", quest.getRawDescription());
        int start = Math.min(descriptionOffset, description.length());
        int end = start + Math.min(2048, description.length() - start);
        return new QuestOutput(hex(quest.getId()), Json.clip(quest.getTitle().getString(), 256),
                Json.clip(quest.getChapter().getTitle().getString(), 128), description.substring(start, end),
                team.getRelativeProgress(quest), team.isCompleted(quest), tasks, rewards,
                new SectionPageOutput(rewardOffset, rewardOffset + rewards.size(), visibleRewards > rewardOffset + rewards.size()),
                new SectionPageOutput(taskOffset, taskOffset + tasks.size(), quest.getTasksAsList().size() > taskOffset + tasks.size()),
                new SectionPageOutput(start, end, description.length() > end));
    }

    record QuestPage(UUID team, long incarnation, long epoch, List<Quest> quests, long expires) {
    }

    static String hex(long value) {
        return String.format(Locale.ROOT, "%016X", value);
    }

    record RewardReference(UUID player, UUID team, long quest, long reward, long completedAt,
                                   Quest questVersion, Reward rewardVersion, int choiceVersion, long expires) {
    }

    static int choiceVersion(Reward reward) {
        if (!(reward instanceof ChoiceReward choice) || choice.getTable() == null) return 0;
        int version = System.identityHashCode(choice.getTable());
        for (WeightedReward entry : choice.getTable().getWeightedRewards())
            version = 31 * version + System.identityHashCode(entry.getReward());
        return version;
    }
}
