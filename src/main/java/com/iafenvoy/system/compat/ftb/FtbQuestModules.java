package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.builtin.*;
import com.iafenvoy.system.assistant.MinecraftTools;

/**
 * Assemble optional single-purpose operations sharing per-server references and quest rules.
 */
public final class FtbQuestModules {
    private FtbQuestModules() {
    }

    public static void install(AgentEngine engine, MinecraftTools game) {
        FtbQuestAccess access = new FtbQuestAccess(engine, game);
        engine.registerModule(new QuestQueryModule(new FtbQuestQueryTool(access).tool()));
        engine.registerModule(new QuestDetailModule(new FtbQuestDetailTool(access).tool()));
        engine.registerModule(new QuestClaimModule(new FtbQuestClaimTool(access).tool()));
        engine.registerModule(new QuestSubmitModule(new FtbQuestSubmitTool(access).tool()));
        engine.registerModule(new StarterGiftModule(new FtbStarterGiftTool(access).tool()));
        engine.questNarration().source(new FtbQuestSpeech(access));
        FtbQuestNotifications.install(game.server(), access);
    }
}
