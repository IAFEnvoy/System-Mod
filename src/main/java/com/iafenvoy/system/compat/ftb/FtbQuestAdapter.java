package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.input.QuestGuideInput;
import com.iafenvoy.system.agent.builtin.output.QuestGuideOutput;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import com.iafenvoy.system.agent.builtin.quest.*;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.PlayerModule;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Resolved only after the optional FTB dependency check.
 */
public final class FtbQuestAdapter {
    private FtbQuestAdapter() {
    }

    public static PlayerModule runtime(MinecraftServer server) {
        return new PlayerModule() {
            @Override
            public Session login(ServerPlayer player) {
                return new Session() {
                    @Override
                    public void initialize(PlayerData data) {
                    }

                    @Override
                    public void close() {
                        FtbQuestNotifications.remove(player);
                    }
                };
            }

            @Override
            public void tick() {
                FtbQuestNotifications.tick(server);
            }

            @Override
            public void close() {
                FtbQuestNotifications.close(server);
            }
        };
    }

    public static List<AgentModule<?>> modules(ModuleContext context) {
        AgentEngine engine = context.engine();
        MinecraftTools game = context.game();
        FtbQuestAccess access = new FtbQuestAccess(engine, game);
        engine.questNarration().source(new FtbQuestSpeech(access));
        FtbQuestNotifications.install(game.server(), access);
        ToolRegistry.Tool<EmptyInput, StarterGiftOutput> gifts = new FtbStarterGiftQueryTool(access).tool();
        ToolRegistry.Tool<QuestDetailInput, QuestOutput> detail = new FtbQuestDetailTool(access).tool();
        ToolRegistry.Tool<QuestGuideInput, QuestGuideOutput> guide = new FtbQuestGuideTool(access).tool();
        engine.tools().registerAll(context.id(), List.of(guide));
        return List.of(new QuestQueryModule(new FtbQuestQueryTool(access).tool()),
                new QuestDetailModule(new FtbQuestFindTool(access).tool(), detail, gifts),
                new QuestGuideModule(guide),
                new QuestClaimModule(new FtbQuestClaimTool(access).tool(), detail),
                new QuestSubmitModule(new FtbQuestSubmitTool(access).tool()),
                new StarterGiftModule(new FtbStarterGiftTool(access).tool()),
                new StarterGiftQueryModule(gifts));
    }

    public static CompletionStage<Integer> reloadKnowledge(MinecraftServer server) {
        return FtbQuestNotifications.reloadKnowledge(server);
    }
}
