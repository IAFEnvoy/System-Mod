package com.iafenvoy.system.screen;

import com.iafenvoy.saturn.screen.hud.HudEditScreen;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.registry.SystemHudEntries;
import com.iafenvoy.system.screen.emotion.EmotionHudState;
import com.iafenvoy.system.screen.emotion.EmotionProfiles;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.screen.gui.EmotionElement;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * A tiny passive document. Frames only repaint; screen changes never rebuild the emotion or its CSS.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AssistantEmotionHud {
    private static Document document;
    private static long generation = -1;
    private static long failedRevision = -1;

    private AssistantEmotionHud() {
    }

    public static boolean mounted() {
        return document != null && !document.isDisposed();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof HudEditScreen) && (minecraft.player == null || minecraft.level == null)) {
            unmount();
            return;
        }
        if (!mounted()) {
            if (!canShow(minecraft.screen) || failedRevision == EmotionProfiles.revision()) return;
            try {
                AssistantPages.prepare(EmotionHudEntry.TEMPLATE);
                document = Document.create(EmotionHudEntry.TEMPLATE);
                if (document == null) throw new IllegalStateException("Missing emotion HUD template");
                document.setReloadPersistent(true);
            } catch (RuntimeException error) {
                failedRevision = EmotionProfiles.revision();
                SystemMod.LOGGER.warn("Unable to create emotion HUD", error);
                unmount();
                return;
            }
        }
        if (generation != document.getRefreshGeneration()) {
            generation = document.getRefreshGeneration();
            SystemHudEntries.EMOTION.detach();
            AssistantThemes.apply(document);
            try (Document.ContextScope ignored = Document.withContext(document)) {
                Element face = document.getElementById("emotion_face");
                if (face == null || document.getElementById("emotion_hud") == null) {
                    failedRevision = EmotionProfiles.revision();
                    SystemMod.LOGGER.warn("Emotion HUD template is missing emotion_hud or emotion_face");
                    unmount();
                    return;
                }
                EmotionElement image = new EmotionElement(document, EmotionHudState.machine(), EmotionHudState::speaking);
                image.setClassName("emotion_image");
                face.appendChild(Element.init(image));
            }
        }
        visibility(minecraft.screen);
        SystemHudEntries.EMOTION.apply(document);
    }

    private static boolean canShow(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        return !minecraft.options.hideGui && SystemHudEntries.EMOTION.visible()
                && (screen instanceof HudEditScreen || EmotionHudState.shown()
                && (screen == null || screen instanceof ChatScreen || screen instanceof AssistantSimpleScreen || screen.isInGameUi()));
    }

    private static void visibility(Screen screen) {
        if (!mounted()) return;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            Element panel = document.getElementById("emotion_hud");
            if (panel == null) return;
            String display = canShow(screen) ? "block" : "none";
            if (!display.equals(panel.getInlineStylePropertyValue("display")))
                panel.setInlineStyleProperty("display", display);
        }
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        visibility(event.getNewScreen());
    }

    public static void unmount() {
        if (document != null) document.remove();
        document = null;
        generation = -1;
        SystemHudEntries.EMOTION.detach();
    }
}
