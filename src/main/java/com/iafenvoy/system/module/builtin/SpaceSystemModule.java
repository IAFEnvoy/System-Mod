package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.space.SystemSpace;
import com.iafenvoy.system.assistant.tool.space.DeliverTool;
import com.iafenvoy.system.data.input.SpaceInput;
import com.iafenvoy.system.data.input.SpaceTakeInput;
import com.iafenvoy.system.data.output.SpaceOutput;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.space.DeliverModule;
import com.iafenvoy.system.module.builtin.space.SpaceModule;
import com.iafenvoy.system.module.builtin.space.SpaceTakeModule;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class SpaceSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox");
    public static final MapCodec<SpaceSystemModule> CODEC = MapCodec.unit(new SpaceSystemModule());
    public static final ModuleStorage<SpaceDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox"), SpaceDataStorage.class, SpaceDataStorage.CODEC, SpaceDataStorage::new);

    @Override
    public MapCodec<SpaceSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        SystemSpace space = context.sharedRuntime(SystemSpace.class, () -> new SystemSpace(context.engine(), context.get(ContextTypes.GAME), context.get(ContextTypes.COMMERCE)));
        ToolRegistry.Tool<SpaceInput, SpaceOutput> query = context.tool(BuiltinTools.SPACE_QUERY,
                () -> new ToolRegistry.Tool<>(BuiltinTools.SPACE_QUERY, false, true, false, (execution, _) -> space.query(execution))
                        .forReasoning("List my system-space contents and their observed IDs without withdrawing anything."));
        ToolRegistry.Tool<SpaceTakeInput, SpaceOutput> take = context.tool(BuiltinTools.SPACE_TAKE,
                () -> new ToolRegistry.Tool<>(BuiltinTools.SPACE_TAKE, true, false, false, space::take));
        return List.of(new SpaceModule(query, context.modules()), new SpaceTakeModule(take),
                new DeliverModule(context.tool(BuiltinTools.DELIVER, () -> new DeliverTool(space).tool()), space));
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return new PlayerModule() {
            @Override
            public Session login(ServerPlayer player) {
                return data -> ModuleData.get(data, STORAGE).recoverDeliveries();
            }
        };
    }
}
