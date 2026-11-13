package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.input.OnlineJobsInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class OnlineJobsSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("online_jobs", "nav_online_jobs", workspace -> {
            workspace.card(CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "module_controls"))
                    .put("title", Component.translatable("system.ui.nav_online_jobs"))
                    .put("description", Component.translatable("system.jobs.note"))
                    .put("action_label", Component.translatable("system.action.job_refresh"))
                    .callback("submit", () -> AssistantClient.send(new OnlineJobsInput(0), "")));
            workspace.click("nav_online_jobs", () -> {
                workspace.show("online_jobs");
                AssistantClient.send(new OnlineJobsInput(0), "");
            });
        });
    }
}
