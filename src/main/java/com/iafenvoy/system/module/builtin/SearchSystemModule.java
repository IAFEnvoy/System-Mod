package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.search.WebFetchTool;
import com.iafenvoy.system.agent.builtin.search.WebSearchModule;
import com.iafenvoy.system.agent.builtin.search.WebSearchTool;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Availability enables registration; only a current explicit request may incur search charges.
 */
public final class SearchSystemModule implements SharedModule {
    public static final ResourceLocation ID = ResourceLocation.parse("system:web_search");
    public static final MapCodec<SearchSystemModule> CODEC = MapCodec.unit(new SearchSystemModule());

    @Override
    public MapCodec<SearchSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new WebSearchModule(new WebSearchTool(context.engine().search()).tool(), new WebFetchTool(context.engine().search()).tool()));
    }
}
