package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.search.WebFetchTool;
import com.iafenvoy.system.agent.builtin.search.WebSearchTool;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ToolModule;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Optional exported tools only; the local-first question workflow owns consent and invocation order.
 */
public final class SearchSystemModule implements ToolModule {
    public static final ResourceLocation ID = ResourceLocation.parse("system:web_search");
    public static final MapCodec<SearchSystemModule> CODEC = MapCodec.unit(new SearchSystemModule());

    @Override
    public MapCodec<SearchSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ToolRegistry.Tool<?, ?>> tools(ModuleContext context) {
        return List.of(new WebSearchTool(context.engine().search()).tool(), new WebFetchTool(context.engine().search()).tool());
    }
}
