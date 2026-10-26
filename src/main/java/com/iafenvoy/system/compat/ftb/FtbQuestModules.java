package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.builtin.QuestClaimModule;
import com.iafenvoy.system.agent.builtin.QuestDetailModule;
import com.iafenvoy.system.agent.builtin.QuestQueryModule;
import com.iafenvoy.system.runtime.server.MinecraftTools;

/** Assemble the optional workflows from three independent operations sharing per-server state. */
public final class FtbQuestModules {
    private FtbQuestModules() {
    }

    public static void install(AgentEngine engine, MinecraftTools game) {
        FtbQuestAccess access = new FtbQuestAccess(engine, game);
        engine.registerModule(new QuestQueryModule(new FtbQuestQueryTool(access).tool()));
        engine.registerModule(new QuestDetailModule(new FtbQuestDetailTool(access).tool()));
        engine.registerModule(new QuestClaimModule(new FtbQuestClaimTool(access).tool()));
    }
}
