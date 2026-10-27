package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestSubmitInput;
import com.iafenvoy.system.agent.builtin.output.QuestSubmitOutput;
import com.iafenvoy.system.agent.builtin.output.QuestTaskOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Native submission consumes only supported item tasks and preserves sequence, pause and dependency rules.
 */
final class FtbQuestSubmitTool implements ToolRegistry.Handler<QuestSubmitInput, QuestSubmitOutput> {
    private final FtbQuestAccess access;

    FtbQuestSubmitTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<QuestSubmitInput, QuestSubmitOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_SUBMIT, true, false, false, this);
    }

    @Override
    public CompletionStage<QuestSubmitOutput> execute(ExecutionContext context, QuestSubmitInput input) {
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("NOT_AUTHORIZED"));
            Quest quest = FtbQuestAccess.selected(context, file, team, input.questId(), input.index().orElse(0), this.access.pages);
            if (quest == null && !input.query().isBlank()) {
                List<Quest> matches = FtbQuestAccess.find(file, team, input.query(), 0);
                if (matches.size() > 1) throw new AgentException("QUEST_AMBIGUOUS");
                quest = matches.isEmpty() ? null : matches.getFirst();
            }
            if (quest == null || !FtbQuestRules.active(team, quest) || team.isProgressionPaused(player))
                throw new AgentException("QUEST_UNAVAILABLE");
            List<ItemTask> tasks = quest.getTasksAsList().stream().filter(task -> task.getClass() == ItemTask.class)
                    .map(ItemTask.class::cast).filter(task -> task.consumesResources() && !task.isTaskScreenOnly()).toList();
            if (tasks.isEmpty()) throw new AgentException("NO_SUBMITTABLE_TASK");
            List<Long> before = tasks.stream().map(team::getProgress).toList();
            Quest target = quest;
            int completions = team.getCompletionCount(target);
            file.withPlayerContext(player, () -> {
                for (ItemTask task : tasks) {
                    // Auto-claimed repeatable quests may reset immediately; one command never enters the next cycle.
                    if (team.isCompleted(target) || team.getCompletionCount(target) != completions || team.isLocked()
                            || !team.canStartTasks(target) || team.isProgressionPaused(player)) break;
                    if (!team.isCompleted(task)) task.submitTask(team, player);
                }
            });
            boolean changed = !before.equals(tasks.stream().map(team::getProgress).toList()) || team.getCompletionCount(target) != completions;
            return new QuestSubmitOutput(FtbQuestAccess.hex(quest.getId()), quest.getTitle().getString(), tasks.stream()
                    .map(task -> new QuestTaskOutput(task.getTitle().getString(), team.getProgress(task), task.getMaxProgress())).toList(), changed, team.isCompleted(quest));
        });
    }
}
