package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.OnlineJobsInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class OnlineJobsSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("online_jobs", "nav_online_jobs", workspace -> {
            workspace.click("nav_online_jobs", () -> {
                workspace.show("online_jobs");
                AssistantClient.send(new OnlineJobsInput(0), "");
            });
            workspace.click("jobs_refresh", () -> AssistantClient.send(new OnlineJobsInput(0), ""));
        });
    }
}
