package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Key declarations and registration stay separate from the input handlers' session state.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class SystemKeyMappings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "assistant"));
    public static final KeyMapping TOGGLE_INTERFACE = new KeyMapping("key.system.toggle_interface", GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping VOICE_INPUT = new KeyMapping("key.system.voice_input", GLFW.GLFW_KEY_F8, CATEGORY);

    private SystemKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_INTERFACE);
        event.register(VOICE_INPUT);
    }
}
