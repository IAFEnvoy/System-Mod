package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
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
        return new ToolRegistry.Tool<>(BuiltinTools.STARTER_GIFTS, false, true, false, this);
    }

    @Override
    public CompletionStage<StarterGiftOutput> execute(ExecutionContext context, EmptyInput input) {
        return this.access.game.onPlayer(context, player -> {
            if (ServerQuestFile.getInstance().isEmpty()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance().orElseThrow();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("NOT_AUTHORIZED"));
            return this.access.gifts.preview(player, file, team);
        });
    }
}
