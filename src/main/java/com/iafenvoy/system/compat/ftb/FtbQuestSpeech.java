package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.QuestNarration;
import com.iafenvoy.system.util.TypeDispatch;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.*;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Server-thread snapshots use exact-class formatters; unfamiliar types retain native titles for model fallback.
 */
final class FtbQuestSpeech implements QuestNarration.Source {
    private static final TypeDispatch<Void, QuestNarration.Line> TASKS = new TypeDispatch<Void, QuestNarration.Line>((_, value) -> unknown((Task) value))
            .register(ItemTask.class, (_, task) -> {
                if (task.getItemStack().isEmpty() || ItemMatchingSystem.INSTANCE.isItemFilter(task.getItemStack()))
                    return unknown(task);
                String key = task.consumesResources() ? "submit" : task.isOnlyFromCrafting() ? "craft" : "obtain";
                return QuestNarration.Line.formatted("system.tts.quest." + key, task.getMaxProgress(), task.getItemStack().getHoverName());
            })
            .register(CheckmarkTask.class, (_, _) -> QuestNarration.Line.formatted("system.tts.quest.checkmark"));
    private static final TypeDispatch<Void, QuestNarration.Line> REWARDS = new TypeDispatch<Void, QuestNarration.Line>((_, value) -> unknown((Reward) value))
            .register(ItemReward.class, (_, reward) -> reward.getItem().isEmpty() ? unknown(reward)
                    : QuestNarration.Line.formatted("system.tts.quest.item", amount(reward.getButtonText()), reward.getItem().getHoverName()))
            .register(XPReward.class, (_, reward) -> QuestNarration.Line.formatted("system.tts.quest.xp", reward.getButtonText().replace("+", "")))
            .register(XPLevelsReward.class, (_, reward) -> QuestNarration.Line.formatted("system.tts.quest.levels", reward.getButtonText().replace("+", "")))
            .register(ChoiceReward.class, (_, reward) -> QuestNarration.Line.formatted("system.tts.quest.choice", reward.getTitle()))
            .register(RandomReward.class, (_, reward) -> QuestNarration.Line.formatted("system.tts.quest.random", reward.getTitle()));
    private final FtbQuestAccess access;

    FtbQuestSpeech(FtbQuestAccess access) {
        this.access = access;
    }

    @Override
    public CompletionStage<QuestNarration.Quest> load(ExecutionContext context, String questId, QuestNarration.Request request) {
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("NOT_AUTHORIZED"));
            Quest quest = file.getQuest(Long.parseUnsignedLong(questId, 16));
            if (quest == null || !FtbQuestReader.detailAvailable(team, quest))
                throw new AgentException("QUEST_UNAVAILABLE");
            List<QuestNarration.Line> requirements = request.requirements() && FtbQuestReader.kind(quest) == FtbQuestReader.Kind.NORMAL ? quest.getTasksAsList().stream()
                    .map(task -> requirement(team, task)).toList() : List.of();
            List<QuestNarration.Line> rewards = request.rewards() ? quest.getRewards().stream()
                    .filter(reward -> !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE)
                    .map(reward -> REWARDS.apply(null, reward)).toList() : List.of();
            boolean showDescription = request.requirements() && request.description() && (!quest.getHideTextUntilComplete().get(quest.getChapter().isHideTextUntilComplete())
                    || team.isCompleted(quest));
            return new QuestNarration.Quest(quest.getTitle().copy(), requirements, rewards,
                    showDescription ? String.join("\n", quest.getRawDescription()) : "");
        });
    }

    private static QuestNarration.Line unknown(Task task) {
        Component text = task.hideProgressNumbers() ? task.getTitle().copy()
                : Component.translatable("system.tts.quest.unknown_requirement", task.getTitle(), task.formatMaxProgress());
        return QuestNarration.Line.unknown(text, "Requirement type: " + task.getType().getTypeId() + "\nTitle: " + task.getTitle().getString()
                + (task.hideProgressNumbers() ? "" : "\nTarget: " + task.formatMaxProgress())
                + (task instanceof ItemTask item ? "\nConsumes inventory on explicit submission: " + item.consumesResources()
                + "\nOnly from crafting: " + (!item.consumesResources() && item.isOnlyFromCrafting()) : ""));
    }

    static QuestNarration.Line requirement(TeamData team, Task task) {
        QuestNarration.Line line = TASKS.apply(null, task);
        return task.isOptionalForProgression(team) ? new QuestNarration.Line(Component.translatable("system.tts.quest.optional", line.text()),
                line.unformatted().map(text -> "Optional requirement.\n" + text)) : line;
    }

    private static Component amount(String value) {
        String[] range = value.split("-", 2);
        return range.length == 2 ? Component.translatable("system.tts.quest.range", range[0], range[1]) : Component.literal(value);
    }

    private static QuestNarration.Line unknown(Reward reward) {
        return QuestNarration.Line.unknown(reward.getTitle().copy(), "Reward type: " + reward.getType().getTypeId()
                + "\nTitle: " + reward.getTitle().getString() + "\nAmount: " + reward.getButtonText());
    }

    static Component rewardText(Reward reward) {
        return REWARDS.apply(null, reward).text();
    }
}
