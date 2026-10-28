package com.iafenvoy.system.module.player;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.output.PlayerSnapshotOutput;
import com.iafenvoy.system.agent.builtin.player.PlayerQueryModule;
import com.iafenvoy.system.agent.builtin.player.PlayerQueryTool;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ServerModule;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class PlayerSystemModule implements ServerModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "player");

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player = context.tool(BuiltinTools.PLAYER, () -> new PlayerQueryTool(context.game()).tool());
        return List.of(new PlayerQueryModule(player));
    }
}
