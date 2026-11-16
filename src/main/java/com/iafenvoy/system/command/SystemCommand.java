package com.iafenvoy.system.command;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Only explicit platform and management commands enter the public game command tree.
 */
@EventBusSubscriber
public final class SystemCommand {
    private SystemCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(SystemMod.MOD_ID);
        CommerceCommands.register(root);
        Set<MapCodec<? extends ServerModule>> types = new HashSet<>();
        event.getBuildContext().lookupOrThrow(SystemResourceKeys.SYSTEM_MODULE).listElements()
                .flatMap(holder -> holder.value().server().stream()).filter(module -> types.add(module.codec()))
                .filter(ServerModule::available)
                .filter(SharedModule.class::isInstance).map(SharedModule.class::cast)
                .forEach(module -> module.serverCommands(root));
        event.getDispatcher().register(root);
    }
}
