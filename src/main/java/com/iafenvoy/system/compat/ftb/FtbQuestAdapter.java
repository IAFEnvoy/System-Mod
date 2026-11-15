package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.assistant.quest.QuestWorkflows;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.PlayerModule;
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
        return QuestWorkflows.install(context, shared(context).provider);
    }

    public static CompletionStage<Integer> reloadKnowledge(MinecraftServer server) {
        AssistantServer runtime = AssistantServer.get(server);
        if (runtime == null) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        return runtime.moduleManager().runtime(FtbQuestRuntime.class)
                .orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE)).access.knowledge.reload();
    }
}
