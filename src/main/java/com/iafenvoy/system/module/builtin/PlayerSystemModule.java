package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.tool.player.PlayerQueryTool;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.output.PlayerSnapshotOutput;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.builtin.player.PlayerQueryModule;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class PlayerSystemModule implements SharedModule {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "player");
    public static final MapCodec<PlayerSystemModule> CODEC = MapCodec.unit(new PlayerSystemModule());

    @Override
    public MapCodec<PlayerSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player = context.tool(BuiltinTools.PLAYER, () -> new PlayerQueryTool(context.get(ContextTypes.GAME)).tool());
        return List.of(new PlayerQueryModule(player));
    }
}
