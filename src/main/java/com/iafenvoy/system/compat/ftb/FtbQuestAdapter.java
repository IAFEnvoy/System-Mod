package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.input.QuestDetailInput;
import com.iafenvoy.system.data.input.QuestGuideInput;
import com.iafenvoy.system.data.output.QuestGuideOutput;
import com.iafenvoy.system.data.output.QuestOutput;
import com.iafenvoy.system.data.output.StarterGiftOutput;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.QuestSystemModule;
import com.iafenvoy.system.module.builtin.quest.*;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Resolved only after the optional FTB dependency check.
 */
public final class FtbQuestAdapter {
    private FtbQuestAdapter() {
    }

    public static PlayerModule runtime(ModuleContext context) {
        return shared(context);
    }

    private static FtbQuestRuntime shared(ModuleContext context) {
        return context.sharedRuntime(FtbQuestRuntime.class, () -> new FtbQuestRuntime(context));
    }

    public static List<AgentModule<?>> modules(ModuleContext context) {
        context.get(ContextTypes.DISPLAYS).register(context.id(), QuestSystemModule.ID, FtbQuestDisplay::read);
        AgentEngine engine = context.engine();
        FtbQuestAccess access = shared(context).access;
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
        AssistantServer runtime = AssistantServer.get(server);
        if (runtime == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
        return runtime.moduleManager().runtime(FtbQuestRuntime.class)
                .orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE")).access.knowledge.reload();
    }
}
