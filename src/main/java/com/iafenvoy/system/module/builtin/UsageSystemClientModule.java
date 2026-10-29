package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.module.client.WorkspaceGroup;

public final class UsageSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> AssistantClient.command("usage"), "usage", "token统计", "token 统计", "用量统计");
        module.command(() -> AssistantClient.command("usage all"), "usage all");
        module.workspace("", "nav_usage", WorkspaceGroup.SETTINGS, workspace -> {
            workspace.labels("nav_usage");
            workspace.click("nav_usage", () -> AssistantClient.command("usage"));
        });
    }
}
