package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.RepairQuoteInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class WorkshopSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("workshop", "nav_workshop", workspace -> {
            workspace.click("nav_workshop", () -> workspace.show("workshop"));
            workspace.click("repair_quote", () -> AssistantClient.send(RepairQuoteInput.INSTANCE, ""));
        });
    }
}
