package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.input.RepairQuoteInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class WorkshopSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("workshop", "nav_workshop", workspace -> {
            workspace.card(CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "module_controls"))
                    .put("title", Component.translatable("system.ui.nav_workshop"))
                    .put("description", Component.translatable("system.workshop.note"))
                    .put("action_label", Component.translatable("system.action.repair_quote"))
                    .callback("submit", () -> AssistantClient.send(RepairQuoteInput.INSTANCE, "")));
            workspace.click("nav_workshop", () -> workspace.show("workshop"));
        });
    }
}
