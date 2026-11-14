package com.iafenvoy.system.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import static com.iafenvoy.system.registry.SystemKeyMappings.TOGGLE_INTERFACE;

/**
 * A remappable, initially unbound key opens the assistant interface.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantKeys {
    private static boolean held;

    private AssistantKeys() {
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (!TOGGLE_INTERFACE.matches(event.getKeyEvent())) return;
        if (event.getAction() == GLFW.GLFW_RELEASE) held = false;
        else if (event.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) press();
    }

    @SubscribeEvent
    public static void screenRelease(ScreenEvent.KeyReleased.Pre event) {
        if (TOGGLE_INTERFACE.matches(event.getKeyEvent())) held = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        InputConstants.Key key = TOGGLE_INTERFACE.getKey();
        if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != event.getButton()) return;
        if (event.getAction() == GLFW.GLFW_RELEASE) held = false;
        else if (event.getAction() == GLFW.GLFW_PRESS && press()) event.setCanceled(true);
    }

    private static boolean press() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null || !minecraft.isWindowActive()
                || !TOGGLE_INTERFACE.isConflictContextAndModifierActive()
                || minecraft.screen != null) return false;
        if (held) return true;
        held = true;
        // The ordinary command checks binding and opens the player's configured interface.
        minecraft.getConnection().sendCommand("system");
        return true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        while (TOGGLE_INTERFACE.consumeClick()) {
            // Input events own the press; queued vanilla clicks must not reopen the interface.
        }
        if (!held) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.isWindowActive()) {
            held = false;
            return;
        }
        InputConstants.Key key = TOGGLE_INTERFACE.getKey();
        boolean down = switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(minecraft.getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
            default -> true;
        };
        if (!down) held = false;
    }
}
