package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
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
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_DETAIL, false, true, false, this);
    }

    @Override
    public CompletionStage<QuestOutput> execute(ExecutionContext context, QuestDetailInput input) {
        if (!input.query().isBlank()) throw new AgentException("INVALID_INPUT");
        String questId = input.questId();
        int index = input.index().orElse(0);
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE"));
            Quest selected = selected(context, file, team, questId, index, this.access.lists);
            if (selected == null || !FtbQuestReader.detailAvailable(team, selected))
                throw new AgentException("QUEST_UNAVAILABLE");
            return detail(player, team, selected, input, this.access.references);
        });
    }
}
