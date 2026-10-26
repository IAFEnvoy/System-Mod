package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestPageOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.*;

/**
 * One FTB operation, loaded only after the optional dependency check.
 */
final class FtbQuestQueryTool implements ToolRegistry.Handler<QuestQueryInput, QuestPageOutput> {
    private final FtbQuestAccess access;

    FtbQuestQueryTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<QuestQueryInput, QuestPageOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_QUERY, false, true, false, this);
    }

    @Override
    public CompletionStage<QuestPageOutput> execute(ExecutionContext context, QuestQueryInput input) {
        String query = input.query().strip().toLowerCase(Locale.ROOT);
        int offset = input.offset();
        boolean claimable = input.claimable();
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE"));
            List<Quest> matches = new ArrayList<>();
            // FTB has no accept step: an active quest is visible, actionable and not completed.
            for (Chapter chapter : file.getAllChapters()) {
                if (chapter.isAlwaysInvisible()) continue;
                for (Quest quest : chapter.getQuests()) {
                    if (!quest.isVisible(team) || team.isLocked()) continue;
                    if (claimable ? !hasClaim(player, team, quest) : team.isCompleted(quest) || !team.canStartTasks(quest))
                        continue;
                    if (!matches(quest, query)) continue;
                    matches.add(quest);
                    if (matches.size() - offset > PAGE_SIZE) break;
                }
                if (matches.size() - offset > PAGE_SIZE) break;
            }
            return page(context, team, matches, offset, this.access.pages);
        });
    }
}
