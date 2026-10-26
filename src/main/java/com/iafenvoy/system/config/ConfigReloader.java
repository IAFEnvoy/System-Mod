package com.iafenvoy.system.config;

import com.iafenvoy.system.SystemMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NonNull;

@EventBusSubscriber
public enum ConfigReloader implements ResourceManagerReloadListener {
    INSTANCE;
    private static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "config_reloader");

    @Override
    public void onResourceManagerReload(@NonNull ResourceManager resourceManager) {
        ConfigInstance.reloadAll();
    }

    @SubscribeEvent
    public static void registerReloader(AddServerReloadListenersEvent event) {
        event.addListener(ID, INSTANCE);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        ConfigInstance.saveAll();
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        ConfigInstance.saveAll();
    }

    @SubscribeEvent
    public static void gameShuttingDown(GameShuttingDownEvent event) {
        ConfigInstance.saveAll();
    }

    /**
     * Client event signatures stay outside the dedicated-server subscriber.
     */
    @EventBusSubscriber(Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent
        public static void registerReloader(AddClientReloadListenersEvent event) {
            event.addListener(ID, INSTANCE);
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            ConfigInstance.saveAll();
        }
    }
}
