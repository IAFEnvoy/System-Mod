package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.SpaceInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class SpaceSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_inbox", workspace -> workspace.click("nav_inbox", () -> {
            workspace.show("chat");
            AssistantClient.send(SpaceInput.INSTANCE, "");
        }));
    }
}
