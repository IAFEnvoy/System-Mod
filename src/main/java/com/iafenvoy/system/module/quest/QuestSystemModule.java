package com.iafenvoy.system.module.quest;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SystemModule;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * This definition is safe without FTB; only the checked factory resolves optional dependency classes.
 */
public final class QuestSystemModule implements SystemModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "ftb_quests");

    @Override
    public Identifier id() {
        return ID;
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
