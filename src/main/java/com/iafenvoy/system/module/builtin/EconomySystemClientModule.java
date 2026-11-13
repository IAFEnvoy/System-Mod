package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.BalanceInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class EconomySystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_economy", workspace -> workspace.click("nav_economy", () -> {
            workspace.show("chat");
            AssistantClient.send(BalanceInput.INSTANCE, "");
        }));
    }
}
