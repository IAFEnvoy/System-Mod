package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.AnchorListInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.screen.gui.AnchorRenameScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class AnchorSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.localAction(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "anchor_rename"), (source, arguments) -> {
            String anchor = arguments.getOrDefault("anchor", "");
            if (anchor.isBlank()) return;
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new AnchorRenameScreen(minecraft.screen, source, anchor, arguments.getOrDefault("name", "")));
        });
        module.workspace("", "nav_anchor", workspace -> workspace.click("nav_anchor", () -> {
            workspace.show("chat");
            AssistantClient.send(AnchorListInput.INSTANCE, "");
        }));
    }
}
