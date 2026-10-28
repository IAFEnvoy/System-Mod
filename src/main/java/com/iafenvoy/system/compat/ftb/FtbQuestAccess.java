package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.assistant.MinecraftTools;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.loot.WeightedReward;
import dev.ftb.mods.ftbquests.quest.reward.ChoiceReward;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared quest list snapshots and reward references; operations are separate single-purpose tools.
 */
final class FtbQuestAccess {
    final MinecraftTools game;
    final Map<String, RewardReference> references = new ConcurrentHashMap<>();
    final Map<UUID, QuestList> lists = new ConcurrentHashMap<>();
    final FtbStarterGifts gifts = new FtbStarterGifts();

    FtbQuestAccess(AgentEngine engine, MinecraftTools game) {
        this.game = game;
        engine.onRevoke(player -> {
            this.references.values().removeIf(reference -> reference.player.equals(player));
            this.lists.remove(player);
            this.gifts.clear(player);
        });
    }

    static Quest selected(ExecutionContext context, ServerQuestFile file, TeamData team, String questId, int index, Map<UUID, QuestList> lists) {
        if (index > 0) {
            QuestList previous = lists.get(context.player());
            if (previous == null || previous.expires() < System.currentTimeMillis()
                    || previous.incarnation() != context.incarnation() || previous.epoch() != context.memoryEpoch()
                    || !previous.team().equals(team.getTeamId())) throw new AgentException("STALE_QUEST_LIST");
            if (index > previous.quests().size()) throw new AgentException("INVALID_SELECTION");
            Quest selected = previous.quests().get(index - 1);
            if (file.getQuest(selected.getId()) != selected) throw new AgentException("STALE_QUEST_LIST");
            return selected;
        }
        return questId.isBlank() ? null : file.getQuest(Long.parseUnsignedLong(questId, 16));
    }

    static Optional<String> reference(ServerPlayer player, TeamData team, Quest quest, Reward reward, Map<String, RewardReference> references) {
        long now = System.currentTimeMillis();
        references.values().removeIf(value -> value.expires < now);
        long owned = references.values().stream().filter(ref -> ref.player.equals(player.getUUID())).count();
        if (!FtbQuestReader.canClaim(player, team, reward) || owned >= 256 || references.size() >= 4096)
            return Optional.empty();
        String reference = UUID.randomUUID().toString();
        references.put(reference, new RewardReference(player.getUUID(), team.getTeamId(), quest.getId(), reward.getId(),
                team.getCompletedTime(quest.getId()).map(Date::getTime).orElse(0L), quest, reward, choiceVersion(reward), now + 120000));
        return Optional.of(reference);
    }

    static List<QuestBriefOutput> list(ExecutionContext context, TeamData team, List<Quest> matches, Map<UUID, QuestList> lists) {
        List<Quest> selected = List.copyOf(matches);
        List<QuestBriefOutput> quests = new ArrayList<>();
        for (Quest quest : selected)
            quests.add(brief(team, quest, quests.size() + 1));
        long now = System.currentTimeMillis();
        lists.values().removeIf(value -> value.expires < now);
        if (!lists.containsKey(context.player()) && lists.size() >= 4096) throw new AgentException("BUSY");
        // Indices refer to the complete displayed list, never to a newly filtered or reordered quest file.
        lists.put(context.player(), new QuestList(team.getTeamId(), context.incarnation(), context.memoryEpoch(), selected, now + 600000));
        return List.copyOf(quests);
    }

    static QuestBriefOutput brief(TeamData team, Quest quest, int number) {
        String text = quest.getChapter().getTitle().getString();
        String text1 = quest.getTitle().getString();
        return new QuestBriefOutput(number, hex(quest.getId()), com.iafenvoy.saturn.util.MiscUtil.clip(text1, 256),
                com.iafenvoy.saturn.util.MiscUtil.clip(text, 128), team.getRelativeProgress(quest));
    }

    static QuestOutput detail(ServerPlayer player, TeamData team, Quest quest, QuestDetailInput input, Map<String, RewardReference> references) {
        int rewardOffset = input.rewardOffset();
        int choiceOffset = input.choiceOffset();
        int taskOffset = input.taskOffset();
        int descriptionOffset = input.descriptionOffset();
        List<QuestRewardOutput> rewards = new ArrayList<>();
        int visibleRewards = 0;
        for (Reward reward : quest.getRewards()) {
            if (team.isRewardBlocked(reward) || reward.getAutoClaimType() == RewardAutoClaim.INVISIBLE) continue;
            if (visibleRewards++ < rewardOffset || rewards.size() == 4) continue;
            boolean claimable = FtbQuestReader.canClaim(player, team, reward);
            Optional<List<QuestChoiceOutput>> choices = Optional.empty();
            boolean hasMoreChoices = false;
            if (reward instanceof ChoiceReward choice && choice.getTable() != null) {
                choices = Optional.of(choice.getTable().getWeightedRewards().stream().skip(choiceOffset).limit(8)
                        .map(candidate -> {
                            String text = candidate.getReward().getTitle().getString();
                            return new QuestChoiceOutput(com.iafenvoy.saturn.util.MiscUtil.clip(text, 256),
                                    previewItems(candidate.getReward()));
                        }).toList());
                hasMoreChoices = choice.getTable().getWeightedRewards().size() > choiceOffset + choices.get().size();
            }
            // Expanded details and completion notices share player/team/object-bound reward references.
            Optional<String> claimRef = claimable ? reference(player, team, quest, reward, references) : Optional.empty();
            if (claimable && claimRef.isEmpty()) claimable = false;
            String text = reward.getTitle().getString();
            rewards.add(new QuestRewardOutput(hex(reward.getId()), com.iafenvoy.saturn.util.MiscUtil.clip(text, 256), claimable,
                    team.getClaimType(player.getUUID(), reward).name(), choices, choices.isPresent() ? choiceOffset : 0, hasMoreChoices, claimRef,
                    previewItems(reward)));
        }
        List<QuestTaskOutput> tasks = quest.getTasksAsList().stream().skip(taskOffset).limit(12)
                .map(task -> {
                    String text = task.getTitle().getString();
                    return new QuestTaskOutput(com.iafenvoy.saturn.util.MiscUtil.clip(text, 256), team.getProgress(task), task.getMaxProgress());
                }).toList();
        String description = quest.getHideTextUntilComplete().get(quest.getChapter().isHideTextUntilComplete()) && !team.isCompleted(quest)
                ? "" : String.join("\n", quest.getRawDescription());
        int start = Math.min(descriptionOffset, description.length());
        int end = start + Math.min(2048, description.length() - start);
        String text = quest.getChapter().getTitle().getString();
        String text1 = quest.getTitle().getString();
        return new QuestOutput(hex(quest.getId()), com.iafenvoy.saturn.util.MiscUtil.clip(text1, 256),
                com.iafenvoy.saturn.util.MiscUtil.clip(text, 128), description.substring(start, end),
                team.getRelativeProgress(quest), team.isCompleted(quest), FtbQuestReader.canComplete(player, team, quest),
                FtbQuestReader.kind(quest) == FtbQuestReader.Kind.STARTER_GIFT, tasks, rewards,
                new SectionPageOutput(rewardOffset, rewardOffset + rewards.size(), visibleRewards > rewardOffset + rewards.size()),
                new SectionPageOutput(taskOffset, taskOffset + tasks.size(), quest.getTasksAsList().size() > taskOffset + tasks.size()),
                new SectionPageOutput(start, end, description.length() > end));
    }

    record QuestList(UUID team, long incarnation, long epoch, List<Quest> quests, long expires) {
    }

    static List<ItemPreviewOutput> previewItems(Reward reward) {
        if (!(reward instanceof ItemReward item) || item.getItem().isEmpty()) return List.of();
        // FTB keeps the base stack separate from its count/range; never mutate that configured stack.
        return List.of(new ItemPreviewOutput(ItemStackTemplate.fromNonEmptyStack(item.getItem()).withCount(1), item.getButtonText()));
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
