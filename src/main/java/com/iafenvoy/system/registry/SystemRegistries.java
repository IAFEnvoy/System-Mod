package com.iafenvoy.system.registry;

import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.module.client.ClientModule;
import net.minecraft.core.Registry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Code-defined module catalogs join the builtin root registry and freeze during mod loading. */
@SuppressWarnings("unused")
@EventBusSubscriber
public final class SystemRegistries {
    public static final Registry<ServerModule> SERVER_MODULE = new RegistryBuilder<>(SystemResourceKeys.SERVER_MODULE).create();

    private SystemRegistries() {
    }

    @SubscribeEvent
    public static void register(NewRegistryEvent event) {
        event.register(SERVER_MODULE);
    }

    /** Client definitions and their registry never load on a dedicated server. */
    @EventBusSubscriber(Dist.CLIENT)
    public static final class Client {
        public static final Registry<ClientModule> CLIENT_MODULE = new RegistryBuilder<>(SystemResourceKeys.Client.CLIENT_MODULE).create();

        private Client() {
        }

        @SubscribeEvent
        public static void register(NewRegistryEvent event) {
            event.register(CLIENT_MODULE);
        }
    }
}
