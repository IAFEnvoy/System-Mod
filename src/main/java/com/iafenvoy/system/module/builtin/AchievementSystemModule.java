package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.progress.Achievements;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.builtin.progress.AchievementQueryModule;
import com.mojang.serialization.MapCodec;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class AchievementSystemModule implements SharedModule {
    public static final MapCodec<AchievementSystemModule> CODEC = MapCodec.unit(new AchievementSystemModule());

    @Override
    public MapCodec<AchievementSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        return List.of(new AchievementQueryModule(new ToolRegistry.Tool<>(BuiltinTools.ACHIEVEMENTS, false, true, false,
                (execution, input) -> CompletableFuture.completedFuture(Achievements.query(execution, input, context.get(ContextTypes.CATALOGS).access())))));
    }
}
