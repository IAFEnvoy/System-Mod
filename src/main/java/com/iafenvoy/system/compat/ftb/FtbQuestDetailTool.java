package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.QuestDetailInput;
import com.iafenvoy.system.data.output.QuestOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.concurrent.CompletionStage;

import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.detail;
import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.selected;

/**
 * Reads exactly one quest by ID or a saved list index; name search belongs to the module workflow.
 */
final class FtbQuestDetailTool implements ToolRegistry.Handler<QuestDetailInput, QuestOutput> {
    private final FtbQuestAccess access;

    FtbQuestDetailTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<QuestDetailInput, QuestOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_DETAIL, false, true, false, this)
                .forReasoning("Read one quest's requirements and rewards. Supply an observed questId or index, never query.");
    }

    @Override
    public CompletionStage<QuestOutput> execute(ExecutionContext context, QuestDetailInput input) {
        if (!input.query().isBlank()) throw new AgentException(AgentFailure.INVALID_INPUT);
        String questId = input.questId();
        int index = input.index().orElse(0);
        return this.access.game.onPlayer(context, player -> {
            if (ServerQuestFile.getInstance().isEmpty()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance().orElseThrow();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE));
            Quest selected = selected(context, file, team, questId, index, this.access.lists);
            if (selected == null || !FtbQuestReader.detailAvailable(team, selected))
                throw new AgentException(AgentFailure.QUEST_UNAVAILABLE);
            return detail(player, team, selected, input, this.access.references);
        });
    }
}
