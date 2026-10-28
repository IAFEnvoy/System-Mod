package com.iafenvoy.system.module.inbox;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.inbox.DeliverModule;
import com.iafenvoy.system.agent.builtin.inbox.DeliverTool;
import com.iafenvoy.system.agent.builtin.inbox.InboxModule;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.module.ModuleData;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class InboxSystemModule implements ServerModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "inbox");
    public static final ModuleStorage<InboxDataStorage> STORAGE = new ModuleStorage<>(ID,
            Identifier.fromNamespaceAndPath("system", "inbox"), InboxDataStorage.class, InboxDataStorage.CODEC, InboxDataStorage::new);

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new InboxModule(), new DeliverModule(new DeliverTool(context.engine(), context.game()).tool()));
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return new PlayerModule() {
            @Override
            public Session login(net.minecraft.server.level.ServerPlayer player) {
                return data -> {
                    for (PlayerData.Delivery delivery : ModuleData.get(data, STORAGE).deliveries.values())
                        if ("DELIVERING".equals(delivery.state)) delivery.state = "DELIVERY_UNKNOWN";
                };
            }
        };
    }
}
