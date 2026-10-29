package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.list;

/**
 * One FTB operation, loaded only after the optional dependency check.
 */
final class FtbQuestQueryTool implements ToolRegistry.Handler<QuestQueryInput, List<QuestBriefOutput>> {
    private final FtbQuestAccess access;

    FtbQuestQueryTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<QuestQueryInput, List<QuestBriefOutput>> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_QUERY, false, true, false, this);
    }

    @Override
    public CompletionStage<List<QuestBriefOutput>> execute(ExecutionContext context, QuestQueryInput input) {
        return this.access.game.onPlayer(context, player -> {
            if (ServerQuestFile.getInstance().isEmpty()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance().orElseThrow();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE"));
            FtbQuestReader reader = new FtbQuestReader(file, team);
            List<Quest> quests = switch (input.view()) {
                case ACTIVE -> reader.active(player);
                case CLAIMABLE -> reader.claimable(player);
                case SUBMITTABLE -> reader.submittable(player);
            };
            List<Quest> matches = quests.stream().filter(quest -> FtbQuestReader.matches(quest, input.query())).toList();
            return list(context, team, matches, this.access.lists);
        });
    }
}
