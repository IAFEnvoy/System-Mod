package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.module.client.WorkspaceGroup;
import com.iafenvoy.system.screen.gui.TokenUsageScreen;
import net.minecraft.client.Minecraft;

public final class UsageSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.command(() -> open(false), "usage", "token统计", "token 统计", "用量统计");
        module.command(() -> open(true), "usage all");
        module.workspace("", "nav_usage", WorkspaceGroup.SETTINGS, workspace -> workspace.click("nav_usage", () -> open(false)));
    }

    private static void open(boolean all) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new TokenUsageScreen(minecraft.screen, all));
    }
}
