package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.assistant.MinecraftTools;

/**
 * Resolve this class only after the optional FTB dependency check.
 */
public final class FtbQuestAdapter {
    private FtbQuestAdapter() {
    }

    public static void install(AgentEngine engine, MinecraftTools game) {
        FtbQuestModules.install(engine, game);
    }
}
