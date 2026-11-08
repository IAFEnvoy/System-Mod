package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.output.QuestPinOutput;
import com.iafenvoy.system.data.output.QuestTaskOutput;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Read only the named quest from the current team, including completed quests; never submit or claim anything.
 */
final class FtbQuestDisplay {
    private FtbQuestDisplay() {
    }

    static Optional<CardData> read(ServerPlayer player, String id) {
        if (ServerQuestFile.getInstance().isEmpty()) return Optional.empty();
        long questId;
        try {
            questId = Long.parseUnsignedLong(id, 16);
        } catch (NumberFormatException error) {
            return Optional.empty();
        }
        ServerQuestFile file = ServerQuestFile.getInstance().orElseThrow();
        TeamData team = file.getTeamData(player).orElse(null);
        Quest quest = file.getQuest(questId);
        if (team == null || quest == null || !FtbQuestReader.detailAvailable(team, quest)) return Optional.empty();
        List<Task> tasks = quest.getTasksAsList();
        List<QuestTaskOutput> preview = tasks.stream().limit(3).map(task -> new QuestTaskOutput(
                FtbQuestText.title(player, task), team.getProgress(task), task.getMaxProgress(), List.of(), List.of())).toList();
        return Optional.of(new QuestPinOutput(id, FtbQuestText.title(player, quest), FtbQuestText.title(player, quest.getChapter()),
                team.getRelativeProgress(quest), team.isCompleted(quest), preview, Math.max(0, tasks.size() - preview.size())));
    }
}
