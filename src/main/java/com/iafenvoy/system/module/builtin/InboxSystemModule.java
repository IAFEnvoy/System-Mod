package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.inbox.DeliverModule;
import com.iafenvoy.system.agent.builtin.inbox.DeliverTool;
import com.iafenvoy.system.agent.builtin.inbox.InboxModule;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.storage.InboxDataStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class InboxSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox");
    public static final MapCodec<InboxSystemModule> CODEC = MapCodec.unit(new InboxSystemModule());
    public static final ModuleStorage<InboxDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox"), InboxDataStorage.class, InboxDataStorage.CODEC, InboxDataStorage::new);

    @Override
    public MapCodec<InboxSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new InboxModule(context.modules()), new DeliverModule(new DeliverTool(context.engine(), context.game()).tool()));
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
