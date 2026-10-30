package com.iafenvoy.system.registry;

import com.iafenvoy.system.data.SystemDefinition;
import com.iafenvoy.system.data.SystemModule;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.List;
import java.util.Optional;

/**
 * Vanilla registry snapshots own systems and configured modules, and synchronize them to each client.
 */
@EventBusSubscriber
public final class SystemDataPackRegistries {
    @SubscribeEvent
    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SystemResourceKeys.SYSTEM, SystemDefinition.CODEC, SystemDefinition.CODEC);
        event.dataPackRegistry(SystemResourceKeys.SYSTEM_MODULE, SystemModule.CODEC, SystemModule.CODEC);
    }

    public static Optional<SystemDefinition> get(RegistryAccess access, ResourceLocation id) {
        // 1.21.1 hands out a plain Registry here; an unknown id yields null rather than an empty Optional.
        return access.registry(SystemResourceKeys.SYSTEM).map(registry -> registry.get(id));
    }

    public static List<ResourceLocation> ids(RegistryAccess access) {
        return access.registry(SystemResourceKeys.SYSTEM)
                .map(registry -> registry.keySet().stream().sorted().toList())
                .orElse(List.of());
    }

    public static Optional<Registry<SystemModule>> modules(RegistryAccess access) {
        return access.registry(SystemResourceKeys.SYSTEM_MODULE);
    }

    public static List<ResourceLocation> moduleIds(RegistryAccess access) {
        return modules(access).map(registry -> registry.keySet().stream().sorted().toList()).orElse(List.of());
    }
}
