package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.QuestFindInput;
import com.iafenvoy.system.data.output.QuestBriefOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
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
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_FIND, false, true, false, this)
                .forReasoning("Find visible quests by name or query and return their actual IDs.");
    }

    @Override
    public CompletionStage<List<QuestBriefOutput>> execute(ExecutionContext context, QuestFindInput input) {
        return this.access.game.onPlayer(context, player -> {
            if (ServerQuestFile.getInstance().isEmpty()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance().orElseThrow();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE));
            List<Quest> matches = new FtbQuestReader(file, team).find(player, input.query());
            // A unique match opens details directly, so it must not replace the player's displayed list indices.
            return matches.size() == 1 ? List.of(brief(player, team, matches.getFirst(), 1))
                    : list(context, player, team, matches, this.access.lists);
        });
    }
}
