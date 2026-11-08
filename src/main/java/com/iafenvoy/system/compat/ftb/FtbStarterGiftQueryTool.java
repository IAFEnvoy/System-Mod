package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.output.StarterGiftOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.concurrent.CompletionStage;

/**
 * Read-only gift enumeration shares visibility and reward checks with binding notifications.
 */
final class FtbStarterGiftQueryTool implements ToolRegistry.Handler<EmptyInput, StarterGiftOutput> {
    private final FtbQuestAccess access;

    FtbStarterGiftQueryTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<EmptyInput, StarterGiftOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.STARTER_GIFTS, false, true, false, this)
                .forReasoning("Preview available starter gift rewards and references. Does not claim or complete anything.");
    }

    @Override
    public CompletionStage<StarterGiftOutput> execute(ExecutionContext context, EmptyInput input) {
        return this.access.game.onPlayer(context, player -> {
            if (!ServerQuestFile.exists()) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            ServerQuestFile file = ServerQuestFile.getInstance();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException(AgentFailure.NOT_AUTHORIZED));
            return this.access.gifts.preview(player, file, team);
        });
    }
}
