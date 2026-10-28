package com.iafenvoy.system.registry;

import com.iafenvoy.system.data.SystemDefinition;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.List;
import java.util.Optional;

/**
 * Vanilla registry snapshots own server definitions and synchronize translation keys to each client.
 */
@EventBusSubscriber
public final class SystemDataPackRegistries {
    @SubscribeEvent
    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SystemResourceKeys.SYSTEM, SystemDefinition.CODEC, SystemDefinition.CODEC);
    }

    public static Optional<SystemDefinition> get(RegistryAccess access, Identifier id) {
        return access.lookup(SystemResourceKeys.SYSTEM).flatMap(registry -> registry.getOptional(id));
    }

    public static List<Identifier> ids(RegistryAccess access) {
        return access.lookup(SystemResourceKeys.SYSTEM).map(registry -> registry.listElementIds()
                .map(ResourceKey::identifier).sorted().toList()).orElse(List.of());
    }
}
