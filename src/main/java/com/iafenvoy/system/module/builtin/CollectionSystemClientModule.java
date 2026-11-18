package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.CollectionQueryInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

/**
 * The server owns collection definitions; the client only provides the paired navigation entry.
 */
public final class CollectionSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_collection", workspace -> workspace.click("nav_collection", () -> {
            workspace.show("chat");
            AssistantClient.send(new CollectionQueryInput("", 0), "");
        }));
    }
}
