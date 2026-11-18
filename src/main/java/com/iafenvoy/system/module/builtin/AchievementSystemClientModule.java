package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.AchievementQueryInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class AchievementSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_achievements", workspace -> workspace.click("nav_achievements", () -> {
            workspace.show("chat");
            AssistantClient.send(new AchievementQueryInput(0), "");
        }));
    }
}
