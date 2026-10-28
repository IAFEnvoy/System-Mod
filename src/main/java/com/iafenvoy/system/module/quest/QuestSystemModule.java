package com.iafenvoy.system.module.quest;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.ServerModule;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * This definition is safe without FTB; only the checked factory resolves optional dependency classes.
 */
public final class QuestSystemModule implements ServerModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "ftb_quests");

    @Override
    public boolean available() {
        return ModList.get().isLoaded("ftbquests");
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        try {
            return (PlayerModule) Class.forName("com.iafenvoy.system.compat.ftb.FtbQuestAdapter").getMethod("runtime", MinecraftServer.class).invoke(null, context.game().server());
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("FTB Quests module runtime is unavailable", error);
        }
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        try {
            Object result = Class.forName("com.iafenvoy.system.compat.ftb.FtbQuestAdapter")
                    .getMethod("modules", AgentEngine.class, MinecraftTools.class).invoke(null, context.engine(), context.game());
            @SuppressWarnings("unchecked") List<AgentModule<?>> modules = (List<AgentModule<?>>) result;
            return List.copyOf(modules);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("FTB Quests assistant adapter is unavailable", error);
        }
    }
}
