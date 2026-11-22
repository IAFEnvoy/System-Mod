package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.tool.player.PlayerQueryTool;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.output.PlayerSnapshotOutput;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.builtin.player.PlayerQueryModule;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class PlayerSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "player");
    public static final MapCodec<PlayerSystemModule> CODEC = MapCodec.unit(new PlayerSystemModule());

    @Override
    public MapCodec<PlayerSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput, ExecutionContext> player = context.tool(BuiltinTools.PLAYER, () -> new PlayerQueryTool(context.get(ContextTypes.GAME)).tool());
        return List.of(new PlayerQueryModule(player));
    }
}
