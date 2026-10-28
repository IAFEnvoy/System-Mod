package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestFindInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.brief;
import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.list;

/**
 * Searches readable quests with exact-name preference and returns only candidate summaries.
 */
final class FtbQuestFindTool implements ToolRegistry.Handler<QuestFindInput, List<QuestBriefOutput>> {
    private final FtbQuestAccess access;

    FtbQuestFindTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<QuestFindInput, List<QuestBriefOutput>> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_FIND, false, true, false, this);
    }

    @Override
    public CompletionStage<List<QuestBriefOutput>> execute(ExecutionContext context, QuestFindInput input) {
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE"));
            List<Quest> matches = new FtbQuestReader(file, team).find(input.query());
            // A unique match opens details directly, so it must not replace the player's displayed list indices.
            return matches.size() == 1 ? List.of(brief(team, matches.getFirst(), 1))
                    : list(context, team, matches, this.access.lists);
        });
    }
}
