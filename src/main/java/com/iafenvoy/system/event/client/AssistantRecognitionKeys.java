package com.iafenvoy.system.event.client;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.speech.asr.AssistantRecognition;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * One remappable hold key routes to the visible notification first, then to the assistant composer.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantRecognitionKeys {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "assistant"));
    private static final KeyMapping RECORD = new KeyMapping("key.system.voice_input", GLFW.GLFW_KEY_F8, CATEGORY);
    private static Screen owner;
    private static AssistantRecognition.Session session;
    private static boolean held;

    private AssistantRecognitionKeys() {
    }

    public static Component translatedKey() {
        return RECORD.getTranslatedKeyMessage();
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(RECORD);
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (!RECORD.matches(event.getKeyEvent())) return;
        if (event.getAction() == GLFW.GLFW_RELEASE) release();
        else if (event.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) press();
    }

    @SubscribeEvent
    public static void screenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AssistantScreen || event.getScreen() instanceof AssistantSimpleScreen
                || event.getScreen() instanceof ChatScreen && AssistantMessages.hasVoiceTarget())
                || !RECORD.matches(event.getKeyEvent()) || !RECORD.isConflictContextAndModifierActive()) return;
        if (press()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void screenRelease(ScreenEvent.KeyReleased.Pre event) {
        if (held && RECORD.matches(event.getKeyEvent())) {
            release();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void character(ScreenEvent.CharacterTyped.Pre event) {
        if (held && event.getScreen() == owner) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton.Pre event) {
        InputConstants.Key key = RECORD.getKey();
        if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != event.getButton()) return;
        if (event.getAction() == GLFW.GLFW_RELEASE && held) {
            release();
            event.setCanceled(true);
        } else if (event.getAction() == GLFW.GLFW_PRESS && press()) event.setCanceled(true);
    }

    private static boolean press() {
        if (held) return true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null || !minecraft.isWindowActive()
                || !RECORD.isConflictContextAndModifierActive()) return false;
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
        while (RECORD.consumeClick()) {
            // Input events own the edge; never replay a queued click after changing screens.
        }
        if (!held) return;
        if (minecraft.screen != owner || minecraft.player == null || !minecraft.isWindowActive() || session == null || !session.active()) {
            if (session != null) session.close();
            owner = null;
            session = null;
            // A cancelled owner must not let key repeats start recording the next notification.
        }
        InputConstants.Key key = RECORD.getKey();
        boolean down = switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(minecraft.getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
            default -> true;
        };
        if (!down || !RECORD.isConflictContextAndModifierActive()) release();
    }
}
