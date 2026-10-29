package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.StarterGiftInput;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;

import java.util.concurrent.CompletionStage;

/**
 * One explicit gift-claim operation, isolated behind the optional FTB dependency check.
 */
final class FtbStarterGiftTool implements ToolRegistry.Handler<StarterGiftInput, StarterGiftOutput> {
    private final FtbQuestAccess access;

    FtbStarterGiftTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<StarterGiftInput, StarterGiftOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.STARTER_GIFT, true, false, false, this);
    }

    @Override
    public CompletionStage<StarterGiftOutput> execute(ExecutionContext context, StarterGiftInput input) {
        return this.access.game.onPlayer(context, player -> {
            if (ServerQuestFile.getInstance().isEmpty()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance().orElseThrow();
            TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("NOT_AUTHORIZED"));
            return this.access.gifts.claim(player, file, team, input.claimRefs());
        });
    }
}
