package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.module.client.ClientModuleBindings;
import com.iafenvoy.system.util.codec.CodecIO;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Highest-priority assets/<namespace>/cards/<path>.json override a card's presentation by payload type ID.
 */
@EventBusSubscriber(Dist.CLIENT)
public enum CardTemplates implements ResourceManagerReloadListener {
    INSTANCE;
    private static Map<Identifier, CardTemplate> templates = Map.of();
    private static long revision;

    @SubscribeEvent
    public static void reloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "cards"), INSTANCE);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        // Complete extension registration before dispatch resolves the first template node.
        ClientModuleBindings.get();
        Map<Identifier, CardTemplate> loaded = new LinkedHashMap<>();
        manager.listResources("cards", id -> id.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            String path = resourceId.getPath();
            Identifier id = Identifier.fromNamespaceAndPath(resourceId.getNamespace(), path.substring(6, path.length() - 5));
            // Fall back through lower resource packs, then to the last valid version of this file.
            List<Resource> layers = manager.getResourceStack(resourceId);
            for (Resource layer : layers.reversed()) {
                try (InputStream stream = layer.open()) {
                    loaded.put(id, CodecIO.parse(CardTemplate.CODEC, new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
                    break;
                } catch (Exception error) {
                    SystemMod.LOGGER.error("Unable to load client card template {}", resourceId, error);
                }
            }
            if (!loaded.containsKey(id) && templates.containsKey(id)) loaded.put(id, templates.get(id));
        });
        templates = Map.copyOf(loaded);
        revision++;
    }

    public static CardTemplate get(Identifier id) {
        return templates.get(id);
    }

    public static long revision() {
        return revision;
    }
}
