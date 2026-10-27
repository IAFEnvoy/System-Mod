package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.util.TypeDispatch;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import dev.ftb.mods.ftbquests.quest.task.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Read-only, server-thread quest views share visibility, classification and native submission semantics.
 * Create a fresh reader for the current file/team; progress and player rewards are never cached.
 */
public final class FtbQuestReader {
    public enum Kind {
        NORMAL, INTRODUCTION, STARTER_GIFT
    }

    public enum Submission {
        NONE, DIRECT, FTB_ONLY
    }

    // Never load TaskClient on a dedicated server. Unknown resource handlers stay with FTB.
    private static final TypeDispatch<Void, Submission> SUBMISSIONS = new TypeDispatch<Void, Submission>((_, value) ->
            ((Task) value).consumesResources() ? Submission.FTB_ONLY : Submission.NONE)
            .register(ItemTask.class, (_, task) -> task.isTaskScreenOnly() ? Submission.FTB_ONLY : task.consumesResources() ? Submission.DIRECT : Submission.NONE)
            .register(CheckmarkTask.class, (_, _) -> Submission.DIRECT)
            .register(XPTask.class, (_, _) -> Submission.DIRECT)
            .register(CustomTask.class, (_, task) -> task.enableButton() && task.autoSubmitOnPlayerTick() <= 0 ? Submission.DIRECT : Submission.NONE);
    private final ServerQuestFile file;
    private final TeamData team;

    public FtbQuestReader(ServerQuestFile file, TeamData team) {
        this.file = file;
        this.team = team;
    }

    // Additional views compose predicates here without duplicating chapter traversal or visibility rules.
    public Stream<Quest> stream(Predicate<Quest> filter) {
        return this.file.getAllChapters().stream().filter(chapter -> !chapter.isAlwaysInvisible())
                .flatMap(chapter -> chapter.getQuests().stream()).filter(quest -> readable(this.team, quest)).filter(filter);
    }

    public List<Quest> active(ServerPlayer player) {
        if (this.team.isProgressionPaused(player)) return List.of();
        return this.stream(quest -> active(this.team, quest)).toList();
    }

    // This view includes locked gifts; availability and unclaimed rewards are separate concerns.
    public List<Quest> unfinishedStarterGifts() {
        return this.stream(quest -> kind(quest) == Kind.STARTER_GIFT && !this.team.isCompleted(quest)).toList();
    }

    // Completed gifts with unclaimed rewards remain available, but paused players cannot submit new gifts.
    public List<Quest> availableStarterGifts(ServerPlayer player) {
        return this.stream(quest -> starterGiftAvailable(player, this.team, quest)).toList();
    }

    public List<Quest> claimable(ServerPlayer player) {
        return this.stream(quest -> quest.getRewards().stream().anyMatch(reward -> canClaim(player, this.team, reward))).toList();
    }

    public List<Quest> manualSubmission(ServerPlayer player) {
        return this.active(player).stream().filter(FtbQuestReader::requiresManualSubmission).toList();
    }

    public List<Quest> notificationBaseline() {
        if (this.team.isLocked()) return List.of();
        // Completed startable quests remain in the baseline so resets are not announced as unlocks.
        return this.stream(quest -> this.team.canStartTasks(quest)
                || this.team.isCompleted(quest) && kind(quest) == Kind.STARTER_GIFT).toList();
    }

    public List<Quest> find(String name) {
        String query = name.strip().toLowerCase(Locale.ROOT);
        List<Quest> matches = this.stream(quest -> matches(quest, query)).toList();
        List<Quest> exact = matches.stream().filter(quest -> quest.getTitle().getString().equalsIgnoreCase(query)
                || String.format(Locale.ROOT, "%016x", quest.getId()).equalsIgnoreCase(query)).toList();
        return exact.isEmpty() ? matches : exact;
    }

    public static boolean matches(Quest quest, String text) {
        String query = text.strip().toLowerCase(Locale.ROOT);
        return query.isBlank() || quest.getTitle().getString().toLowerCase(Locale.ROOT).contains(query)
                || String.format(Locale.ROOT, "%016x", quest.getId()).equalsIgnoreCase(query);
    }

    public static Kind kind(Quest quest) {
        if (!quest.getTasksAsList().stream().allMatch(task -> task.getClass() == CheckmarkTask.class))
            return Kind.NORMAL;
        if (quest.getRewards().isEmpty()) return Kind.INTRODUCTION;
        if (!quest.getTasksAsList().isEmpty() && !quest.canBeRepeated() && quest.getRewards().stream().allMatch(reward ->
                reward.getClass() == ItemReward.class && !((ItemReward) reward).getItem().isEmpty()
                        && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE)) return Kind.STARTER_GIFT;
        return Kind.NORMAL;
    }

    public static boolean readable(TeamData team, Quest quest) {
        return kind(quest) != Kind.INTRODUCTION && !quest.getChapter().isAlwaysInvisible() && quest.isVisible(team);
    }

    public static boolean detailAvailable(TeamData team, Quest quest) {
        return readable(team, quest) && (!quest.hideDetailsUntilStartable() || team.canStartTasks(quest));
    }

    public static boolean active(TeamData team, Quest quest) {
        return !team.isLocked() && readable(team, quest) && !team.isCompleted(quest) && team.canStartTasks(quest);
    }

    public static boolean canComplete(ServerPlayer player, TeamData team, Quest quest) {
        if (!active(team, quest) || team.isProgressionPaused(player)) return false;
        List<Task> remaining = quest.getTasksAsList().stream().filter(task -> !team.isCompleted(task)).toList();
        // The completion button never submits a remaining resource-consuming or third-party task.
        return !remaining.isEmpty() && remaining.stream().allMatch(task -> task.getClass() == CheckmarkTask.class);
    }

    public static boolean canClaim(ServerPlayer player, TeamData team, Reward reward) {
        return !team.isLocked() && readable(team, reward.getQuest()) && team.isCompleted(reward.getQuest())
                && !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE
                && "CAN_CLAIM".equals(team.getClaimType(player.getUUID(), reward).name());
    }

    public static boolean starterGiftAvailable(ServerPlayer player, TeamData team, Quest quest) {
        return kind(quest) == Kind.STARTER_GIFT && readable(team, quest) && !team.isLocked()
                && (team.isCompleted(quest) || active(team, quest) && !team.isProgressionPaused(player))
                && quest.getRewards().stream().anyMatch(reward -> !team.isRewardBlocked(reward)
                && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE && !team.isRewardClaimed(player.getUUID(), reward));
    }

    public static Submission submission(Task task) {
        return SUBMISSIONS.apply(null, task);
    }

    public static boolean requiresManualSubmission(Quest quest) {
        // Classify the whole quest, including optional or already completed tasks.
        return quest.getTasksAsList().stream().anyMatch(task -> submission(task) != Submission.NONE);
    }

    public static List<Task> submittableTasks(Quest quest) {
        return quest.getTasksAsList().stream().filter(task -> submission(task) == Submission.DIRECT).toList();
    }
}
