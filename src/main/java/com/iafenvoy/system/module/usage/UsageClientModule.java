package com.iafenvoy.system.module.usage;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;

public final class UsageClientModule implements ClientModule {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("system", "usage");
    }

    @Override
    public boolean serverRequired() {
        return false;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> AssistantClient.command("usage"), "usage", "token统计", "token 统计", "用量统计");
        module.command(() -> AssistantClient.command("usage all"), "usage all");
        module.workspace("", "nav_usage", workspace -> {
            workspace.labels("nav_usage");
            workspace.click("nav_usage", () -> AssistantClient.command("usage"));
        });
    }

}
