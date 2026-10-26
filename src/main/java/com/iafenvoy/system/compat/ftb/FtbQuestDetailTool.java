package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.QuestDetailOutput;
import com.iafenvoy.system.agent.builtin.output.QuestLookupOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import java.util.*;
import java.util.concurrent.CompletionStage;
import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.*;

/** One FTB operation, loaded only after the optional dependency check. */
final class FtbQuestDetailTool implements ToolRegistry.Handler<QuestDetailInput, QuestLookupOutput> {
    private final FtbQuestAccess access;

    FtbQuestDetailTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<QuestDetailInput, QuestLookupOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_DETAIL, false, true, false, this);
    }

    @Override
    public CompletionStage<QuestLookupOutput> execute(ExecutionContext context, QuestDetailInput input) {
        String query = input.query().strip().toLowerCase(Locale.ROOT);
        String questId = input.questId();
        int index = input.index().orElse(0);
        int offset = input.offset();
        int selectors = (query.isBlank() ? 0 : 1) + (questId.isBlank() ? 0 : 1) + (index == 0 ? 0 : 1);
        if (selectors != 1 || !questId.isBlank() && !questId.matches("[0-9a-fA-F]{16}"))
            throw new AgentException("INVALID_INPUT");
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE"));
            Quest selected;
            if (index > 0) {
                QuestPage previous = this.access.pages.get(player.getUUID());
                if (previous == null || previous.expires() < System.currentTimeMillis()
                        || previous.incarnation() != context.incarnation() || previous.epoch() != context.memoryEpoch()
                        || !previous.team().equals(team.getTeamId())) throw new AgentException("STALE_QUEST_LIST");
                if (index > previous.quests().size()) throw new AgentException("INVALID_SELECTION");
                selected = previous.quests().get(index - 1);
                if (file.getQuest(selected.getId()) != selected) throw new AgentException("STALE_QUEST_LIST");
            } else if (!questId.isBlank()) selected = file.getQuest(Long.parseUnsignedLong(questId, 16));
            else {
                List<Quest> exact = new ArrayList<>();
                List<Quest> partial = new ArrayList<>();
                for (Chapter chapter : file.getAllChapters()) {
                    if (chapter.isAlwaysInvisible()) continue;
                    for (Quest quest : chapter.getQuests()) {
                        if (!quest.isVisible(team)) continue;
                        if (quest.getTitle().getString().equalsIgnoreCase(query) || hex(quest.getId()).equalsIgnoreCase(query)) {
                            if (exact.size() <= offset + PAGE_SIZE) exact.add(quest);
                        }
                        else if (matches(quest, query) && partial.size() <= offset + PAGE_SIZE) partial.add(quest);
                    }
                }
                List<Quest> matches = exact.isEmpty() ? partial : exact;
                if (matches.size() != 1) return page(context, team, matches, offset, this.access.pages);
                selected = matches.getFirst();
            }
            if (selected == null || selected.getChapter().isAlwaysInvisible() || !selected.isVisible(team)
                    || selected.hideDetailsUntilStartable() && !team.canStartTasks(selected))
                throw new AgentException("QUEST_UNAVAILABLE");
            return new QuestDetailOutput(detail(player, team, selected, input, this.access.references));
        });
    }
}
