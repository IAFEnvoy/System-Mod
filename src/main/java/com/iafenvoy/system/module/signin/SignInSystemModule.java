package com.iafenvoy.system.module.signin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.output.PlayerSnapshotOutput;
import com.iafenvoy.system.agent.builtin.player.PlayerQueryTool;
import com.iafenvoy.system.agent.builtin.signin.SignInCreditTool;
import com.iafenvoy.system.agent.builtin.signin.SignInModule;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SystemModule;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class SignInSystemModule implements SystemModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "signin");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player = context.tool(BuiltinTools.PLAYER, () -> new PlayerQueryTool(context.game()).tool());
        return List.of(new SignInModule(player, new SignInCreditTool().tool()));
    }
}
