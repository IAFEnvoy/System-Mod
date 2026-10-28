package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.SystemDefinition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.Optional;

/**
 * Vanilla registry snapshots own server definitions and synchronize translation keys to each client.
 */
@SuppressWarnings("unused")
@EventBusSubscriber
public final class SystemDataPackRegistries {
    public static final Identifier DEFAULT = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "default");

    @SubscribeEvent
    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SystemResourceKeys.SYSTEM, SystemDefinition.CODEC, SystemDefinition.CODEC);
    }

    public static Optional<SystemDefinition> get(RegistryAccess access, Identifier id) {
        return access.lookup(SystemResourceKeys.SYSTEM).flatMap(registry -> registry.getOptional(id));
    }

    // Selection is currently server-wide; other entries are available for future binding/module selection.
    public static SystemDefinition current(RegistryAccess access) {
        return get(access, DEFAULT).orElse(SystemDefinition.DEFAULT);
    }
}
