package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.quest.*;
import com.iafenvoy.system.assistant.MinecraftTools;
import net.minecraft.world.level.storage.LevelResource;

import java.util.List;

/**
 * Resolved only after the optional FTB dependency check.
 */
public final class FtbQuestAdapter {
    private FtbQuestAdapter() {
    }

    public static List<AgentModule<?>> modules(AgentEngine engine, MinecraftTools game) {
        FtbQuestAccess access = new FtbQuestAccess(engine, game);
        engine.questNarration().cacheFile(game.server().getWorldPath(LevelResource.ROOT).resolve("system-quest-backgrounds.json"));
        engine.questNarration().source(new FtbQuestSpeech(access));
        FtbQuestNotifications.install(game.server(), access);
        return List.of(new QuestQueryModule(new FtbQuestQueryTool(access).tool()),
                new QuestDetailModule(new FtbQuestDetailTool(access).tool()),
                new QuestClaimModule(new FtbQuestClaimTool(access).tool()),
                new QuestSubmitModule(new FtbQuestSubmitTool(access).tool()),
                new StarterGiftModule(new FtbStarterGiftTool(access).tool()),
                new StarterGiftQueryModule(new FtbStarterGiftQueryTool(access).tool()));
    }
}
