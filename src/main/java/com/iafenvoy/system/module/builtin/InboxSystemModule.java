package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.inbox.DeliverModule;
import com.iafenvoy.system.agent.builtin.inbox.DeliverTool;
import com.iafenvoy.system.agent.builtin.inbox.InboxModule;
import com.iafenvoy.system.agent.builtin.input.InboxInput;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.storage.InboxDataStorage;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class InboxSystemModule implements SharedModule {
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
            public Session login(ServerPlayer player) {
                return data -> {
                    for (PlayerData.Delivery delivery : ModuleData.get(data, STORAGE).deliveries.values())
                        if ("DELIVERING".equals(delivery.state)) delivery.state = "DELIVERY_UNKNOWN";
                };
            }
        };
    }

    @Override
    public void register(ClientModuleContext module) {
        ClientHooks.register(module);
    }

    // The dedicated server loads the shared module, but never resolves these UI implementations.
    private static final class ClientHooks {
        private static void register(ClientModuleContext module) {
            module.workspace("", "nav_inbox", workspace -> {
                workspace.labels("nav_inbox");
                workspace.click("nav_inbox", () -> {
                    workspace.show("chat");
                    AssistantClient.send(InboxInput.INSTANCE, "");
                });
            });
        }
    }
}
