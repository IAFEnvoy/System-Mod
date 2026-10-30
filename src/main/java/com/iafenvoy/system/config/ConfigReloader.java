package com.iafenvoy.system.config;

import com.iafenvoy.system.SystemMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

@EventBusSubscriber
public enum ConfigReloader implements ResourceManagerReloadListener {
    INSTANCE;
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "config_reloader");

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        ConfigInstance.reloadAll();
    }

    @SubscribeEvent
    public static void registerReloader(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
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
        public static void registerReloader(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(INSTANCE);
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            ConfigInstance.saveAll();
        }
    }
}
