package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.QuestQueryInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

/**
 * Native and optional quest providers share the common list/detail cards.
 */
public final class CommissionSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_commission", workspace -> workspace.click("nav_commission", () -> {
            workspace.show("chat");
            AssistantClient.send(new QuestQueryInput("system:commission#*", QuestQueryInput.View.ACTIVE), "");
        }));
    }
}
