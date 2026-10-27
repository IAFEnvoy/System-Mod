package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.QuestDetailOutput;
import com.iafenvoy.system.agent.builtin.output.QuestLookupOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

import static com.iafenvoy.system.compat.ftb.FtbQuestAccess.*;

/**
 * One FTB operation, loaded only after the optional dependency check.
 */
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
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE"));
            Quest selected = selected(context, file, team, questId, index, this.access.lists);
            if (questId.isBlank() && index == 0) {
                List<Quest> matches = new FtbQuestReader(file, team).find(query);
                if (matches.size() != 1) return list(context, team, matches, this.access.lists);
                selected = matches.getFirst();
            }
            if (selected == null || !FtbQuestReader.detailAvailable(team, selected))
                throw new AgentException("QUEST_UNAVAILABLE");
            if (FtbQuestReader.kind(selected) == FtbQuestReader.Kind.STARTER_GIFT)
                return this.access.gifts.preview(player, file, team);
            return new QuestDetailOutput(detail(player, team, selected, input, this.access.references));
        });
    }
}
