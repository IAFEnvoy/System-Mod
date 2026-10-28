package com.iafenvoy.system.module.inbox;

import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemActionTypes;

public final class InboxClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_inbox", workspace -> {
            workspace.labels("nav_inbox");
            workspace.click("nav_inbox", () -> {
                workspace.show("chat");
                AssistantClient.send(SystemActionTypes.INBOX_ACTION.get().of(EmptyInput.INSTANCE), "");
            });
        });
    }
}
