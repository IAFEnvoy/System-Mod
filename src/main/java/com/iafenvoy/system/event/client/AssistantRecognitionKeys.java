package com.iafenvoy.system.event.client;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.speech.asr.AssistantRecognition;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import static com.iafenvoy.system.registry.SystemKeyMappings.VOICE_INPUT;

/**
 * One remappable hold key routes to the visible notification first, then to the assistant composer.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantRecognitionKeys {
    private static Screen owner;
    private static AssistantRecognition.Session session;
    private static boolean held;

    private AssistantRecognitionKeys() {
    }

    public static Component translatedKey() {
        return VOICE_INPUT.getTranslatedKeyMessage();
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (!VOICE_INPUT.matches(event.getKey(), event.getScanCode())) return;
        if (event.getAction() == GLFW.GLFW_RELEASE) release();
        else if (event.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) press();
    }

    @SubscribeEvent
    public static void screenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AssistantScreen || event.getScreen() instanceof AssistantSimpleScreen
                || event.getScreen() instanceof ChatScreen && AssistantMessages.hasVoiceTarget())
                || !VOICE_INPUT.matches(event.getKeyCode(), event.getScanCode()) || !VOICE_INPUT.isConflictContextAndModifierActive())
            return;
        if (press()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void screenRelease(ScreenEvent.KeyReleased.Pre event) {
        if (held && VOICE_INPUT.matches(event.getKeyCode(), event.getScanCode())) {
            boolean consumed = ownsInput(event.getScreen());
            release();
            if (consumed) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void character(ScreenEvent.CharacterTyped.Pre event) {
        if (ownsInput(event.getScreen())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton.Pre event) {
        InputConstants.Key key = VOICE_INPUT.getKey();
        if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != event.getButton()) return;
        if (event.getAction() == GLFW.GLFW_RELEASE && held) {
            boolean consumed = ownsInput(Minecraft.getInstance().screen);
            release();
            if (consumed) event.setCanceled(true);
        } else if (event.getAction() == GLFW.GLFW_PRESS && press()) event.setCanceled(true);
    }

    private static boolean press() {
        Minecraft minecraft = Minecraft.getInstance();
        if (held) return ownsInput(minecraft.screen);
        if (minecraft.player == null || minecraft.getConnection() == null || !minecraft.isWindowActive()
                || !VOICE_INPUT.isConflictContextAndModifierActive()) return false;
        if (AssistantMessages.hasVoiceTarget()) {
            session = AssistantMessages.startRecognition();
            if (session != null) {
                owner = minecraft.screen;
                held = true;
            }
            return true;
        }
        if (AssistantClient.state().busy()) return false;
        if (minecraft.screen == null) {
            if (!RecognitionConfig.INSTANCE.get().enabled()) {
                minecraft.setScreen(new AssistantSettingsScreen(null, AssistantSettingsScreen.Tab.ASR));
                return true;
            }
            AssistantClient.open(AssistantClient.simpleMode(), "");
        }
        if (!(minecraft.screen instanceof AssistantScreen || minecraft.screen instanceof AssistantSimpleScreen))
            return false;
        AssistantInputScreen screen = (AssistantInputScreen) minecraft.screen;
        if (!screen.startRecognition()) return false;
        owner = screen;
        session = screen.recognitionSession();
        held = true;
        return true;
    }

    private static boolean ownsInput(Screen screen) {
        // A held-key latch may outlive its recording; it must not suppress unrelated typing.
        return held && session != null && !session.finished() && session.active()
                && screen == owner && Minecraft.getInstance().isWindowActive();
    }

    private static void release() {
        if (session != null) {
            if (session.active()) session.finish();
            else session.close();
        }
        held = false;
        owner = null;
        session = null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (VOICE_INPUT.consumeClick()) {
            // Input events own the edge; never replay a queued click after changing screens.
        }
        if (!held) return;
        if (minecraft.player == null || !minecraft.isWindowActive()) {
            release();
            return;
        }
        if (minecraft.screen != owner || session == null || session.finished() || !session.active()) {
            if (session != null) session.close();
            owner = null;
            session = null;
            // A cancelled owner must not let key repeats start recording the next notification.
        }
        InputConstants.Key key = VOICE_INPUT.getKey();
        boolean down = switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
            default -> true;
        };
        if (!down || !VOICE_INPUT.isConflictContextAndModifierActive()) release();
    }
}
