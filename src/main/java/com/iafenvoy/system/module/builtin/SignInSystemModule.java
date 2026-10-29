package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.output.PlayerSnapshotOutput;
import com.iafenvoy.system.agent.builtin.player.PlayerQueryTool;
import com.iafenvoy.system.agent.builtin.signin.SignInCreditTool;
import com.iafenvoy.system.agent.builtin.signin.SignInModule;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.builtin.storage.SignInDataStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class SignInSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "signin");
    public static final MapCodec<SignInSystemModule> CODEC = MapCodec.unit(new SignInSystemModule());
    public static final ModuleStorage<SignInDataStorage> STORAGE = new ModuleStorage<>(ID,
            Identifier.fromNamespaceAndPath("system", "sign_in"), SignInDataStorage.class, SignInDataStorage.CODEC, SignInDataStorage::new);

    @Override
    public MapCodec<SignInSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player = context.tool(BuiltinTools.PLAYER, () -> new PlayerQueryTool(context.game()).tool());
        return List.of(new SignInModule(player, new SignInCreditTool().tool()));
    }
}
