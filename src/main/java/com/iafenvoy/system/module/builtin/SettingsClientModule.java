package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.module.client.WorkspaceGroup;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;

public final class SettingsClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.localAction(Identifier.parse("system:settings"), (_, _) -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new AssistantSettingsScreen(minecraft.screen, AssistantSettingsScreen.Tab.GENERAL));
        });
        module.command(SettingsClientModule::open, "settings", "设置", "系统设置");
        module.workspace("", "nav_settings", WorkspaceGroup.SETTINGS, workspace -> {
            workspace.labels("nav_settings");
            workspace.click("nav_settings", SettingsClientModule::open);
        });
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen instanceof ChatScreen ? null : minecraft.screen;
        minecraft.setScreen(new AssistantSettingsScreen(parent, AssistantSettingsScreen.Tab.GENERAL));
    }

    @Override
    public void clientCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("settings").executes(context -> {
            open();
            return 1;
        }));
    }
}
