package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.builtin.input.PlayerQueryInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class PlayerSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_player", workspace -> {
            workspace.labels("nav_player");
            workspace.click("nav_player", () -> {
                workspace.show("chat");
                AssistantClient.send(PlayerQueryInput.INSTANCE, "");
            });
        });
    }
}
