package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.AnchorListInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class AnchorSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_anchor", workspace -> workspace.click("nav_anchor", () -> {
            workspace.show("chat");
            AssistantClient.send(AnchorListInput.INSTANCE, "");
        }));
    }
}
