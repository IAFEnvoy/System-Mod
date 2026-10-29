package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.builtin.input.InboxInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class InboxSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_inbox", workspace -> {
            workspace.labels("nav_inbox");
            workspace.click("nav_inbox", () -> {
                workspace.show("chat");
                AssistantClient.send(InboxInput.INSTANCE, "");
            });
        });
    }
}
