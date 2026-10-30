package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.QuestNarration;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.card.SpeechContent;
import com.iafenvoy.system.module.builtin.QuestSystemModule;
import com.iafenvoy.system.util.LegacyText;
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
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Server-thread snapshots use exact-class formatters; unfamiliar types retain native titles for model fallback.
 */
final class FtbQuestSpeech implements QuestNarration.Source {
    private static final TypeDispatch<ServerPlayer, QuestNarration.Line> TASKS = new TypeDispatch<ServerPlayer, QuestNarration.Line>((player, value) -> unknown(player, (Task) value))
            .register(ItemTask.class, (player, task) -> {
                if (task.getItemStack().isEmpty() || ItemMatchingSystem.INSTANCE.isItemFilter(task.getItemStack()))
                    return unknown(player, task);
                String key = task.consumesResources() ? "submit" : task.isOnlyFromCrafting() ? "craft" : "obtain";
                return QuestNarration.Line.formatted("system.tts.quest." + key, task.getMaxProgress(), task.getItemStack().getHoverName());
            })
            .register(CheckmarkTask.class, (unused1, unused2) -> QuestNarration.Line.formatted("system.tts.quest.checkmark"));
    private static final TypeDispatch<ServerPlayer, QuestNarration.Line> REWARDS = new TypeDispatch<ServerPlayer, QuestNarration.Line>((player, value) -> unknown(player, (Reward) value))
            .register(ItemReward.class, (player, reward) -> reward.getItem().isEmpty() ? unknown(player, reward)
                    : QuestNarration.Line.formatted("system.tts.quest.item", amount(reward.getButtonText()), reward.getItem().getHoverName()))
            .register(XPReward.class, (unused1, reward) -> QuestNarration.Line.formatted("system.tts.quest.xp", reward.getButtonText().replace("+", "")))
            .register(XPLevelsReward.class, (unused1, reward) -> QuestNarration.Line.formatted("system.tts.quest.levels", reward.getButtonText().replace("+", "")))
            .register(ChoiceReward.class, (player, reward) -> QuestNarration.Line.formatted("system.tts.quest.choice", FtbQuestText.title(player, reward)))
            .register(RandomReward.class, (player, reward) -> QuestNarration.Line.formatted("system.tts.quest.random", FtbQuestText.title(player, reward)));
    private final FtbQuestAccess access;

    FtbQuestSpeech(FtbQuestAccess access) {
        this.access = access;
    }

    @Override
    public CompletionStage<QuestNarration.Quest> load(ExecutionContext context, String questId) {
        return this.access.game.onPlayer(context, player -> {
            AssistantServer runtime = AssistantServer.get(player.level().getServer());
            if (runtime == null || !runtime.moduleManager().hasType(player.getUUID(), QuestSystemModule.CODEC))
                throw new AgentException("CAPABILITY_UNAVAILABLE");
            if (ServerQuestFile.getInstance().isEmpty()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance().orElseThrow();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("NOT_AUTHORIZED"));
            Quest quest = file.getQuest(Long.parseUnsignedLong(questId, 16));
            if (quest == null || !FtbQuestReader.detailAvailable(team, quest))
                throw new AgentException("QUEST_UNAVAILABLE");
            return snapshot(player, team, quest);

        });
    }

    static QuestNarration.Quest snapshot(ServerPlayer player, TeamData team, Quest quest) {
        List<QuestNarration.Line> requirements = FtbQuestReader.kind(quest) == FtbQuestReader.Kind.NORMAL
                ? quest.getTasksAsList().stream().map(task -> requirement(player, team, task)).toList() : List.of();
        List<QuestNarration.Line> rewards = quest.getRewards().stream()
                .filter(reward -> !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE)
                .map(reward -> REWARDS.apply(player, reward)).toList();
        return new QuestNarration.Quest(FtbQuestText.title(player, quest), requirements, rewards,
                LegacyText.plain(FtbQuestText.description(player, team, quest)));
    }

    static SpeechContent content(ServerPlayer player, TeamData team, Quest quest, String event) {
        Component main = Component.translatable("system.tts.message.quest_" + event, FtbQuestText.title(player, quest));
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        return runtime == null ? SpeechContent.of(main)
                : runtime.engine().questNarration().content(player.getUUID(), FtbQuestAccess.hex(quest.getId()), snapshot(player, team, quest), main);
    }

    private static QuestNarration.Line unknown(ServerPlayer player, Task task) {
        Component title = FtbQuestText.title(player, task);
        Component text = task.hideProgressNumbers() ? title
                : Component.translatable("system.tts.quest.unknown_requirement", title, task.formatMaxProgress());
        return QuestNarration.Line.unknown(text, "Requirement type: " + task.getType().getTypeId() + "\nTitle: " + title.getString()
                + (task.hideProgressNumbers() ? "" : "\nTarget: " + task.formatMaxProgress())
                + (task instanceof ItemTask item ? "\nConsumes inventory on explicit submission: " + item.consumesResources()
                + "\nOnly from crafting: " + (!item.consumesResources() && item.isOnlyFromCrafting()) : ""));
    }

    static QuestNarration.Line requirement(ServerPlayer player, TeamData team, Task task) {
        QuestNarration.Line line = TASKS.apply(player, task);
        return task.isOptionalForProgression(team) ? new QuestNarration.Line(Component.translatable("system.tts.quest.optional", line.text()),
                line.unformatted().map(text -> "Optional requirement.\n" + text)) : line;
    }

    private static Component amount(String value) {
        String[] range = value.split("-", 2);
        return range.length == 2 ? Component.translatable("system.tts.quest.range", range[0], range[1]) : Component.literal(value);
    }

    private static QuestNarration.Line unknown(ServerPlayer player, Reward reward) {
        Component title = FtbQuestText.title(player, reward);
        return QuestNarration.Line.unknown(title, "Reward type: " + reward.getType().getTypeId()
                + "\nTitle: " + title.getString() + "\nAmount: " + reward.getButtonText());
    }

    static Component rewardText(ServerPlayer player, Reward reward) {
        return REWARDS.apply(player, reward).text();
    }
}
