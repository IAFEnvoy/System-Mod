package com.iafenvoy.system.module.player;

import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemActionTypes;

public final class PlayerClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_player", workspace -> {
            workspace.labels("nav_player");
            workspace.click("nav_player", () -> {
                workspace.show("chat");
                AssistantClient.send(SystemActionTypes.PLAYER_ACTION.get().of(EmptyInput.INSTANCE), "");
            });
        });
    }
}
