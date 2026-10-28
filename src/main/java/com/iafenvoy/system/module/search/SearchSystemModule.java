package com.iafenvoy.system.module.search;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.search.WebFetchTool;
import com.iafenvoy.system.agent.builtin.search.WebSearchModule;
import com.iafenvoy.system.agent.builtin.search.WebSearchTool;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ServerModule;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Availability enables registration; only a current explicit request may incur search charges.
 */
public final class SearchSystemModule implements ServerModule {
    public static final Identifier ID = Identifier.parse("system:web_search");

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new WebSearchModule(new WebSearchTool(context.engine().search()).tool(), new WebFetchTool(context.engine().search()).tool()));
    }
}
