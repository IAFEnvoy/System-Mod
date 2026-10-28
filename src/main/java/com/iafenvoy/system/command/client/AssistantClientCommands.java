package com.iafenvoy.system.command.client;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.client.ClientModules;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;

/**
 * Bridges game and assistant inputs to feature-owned client command declarations.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantClientCommands {
    private AssistantClientCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(SystemMod.MOD_ID);
        ClientModules.registry().commands(root);
        event.getDispatcher().register(root);
    }

    public static List<String> suggestions() {
        return ClientModules.registry().commands().suggestions();
    }

    public static boolean execute(String text) {
        return ClientModules.registry().commands().execute(text);
    }
}
