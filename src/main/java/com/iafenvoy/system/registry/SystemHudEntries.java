package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.screen.hud.HudManager;
import com.iafenvoy.system.screen.DisplayCardsHudEntry;
import com.iafenvoy.system.screen.NotificationHudEntry;
import com.iafenvoy.system.screen.EmotionHudEntry;

/**
 * Initialized only by the physical client entrypoint; Saturn owns the shared HUD editor and layout file.
 */
public final class SystemHudEntries {
    public static final NotificationHudEntry NOTIFICATIONS = HudManager.register(new NotificationHudEntry());
    public static final DisplayCardsHudEntry DISPLAY_CARDS = HudManager.register(new DisplayCardsHudEntry());
    public static final EmotionHudEntry EMOTION = HudManager.register(new EmotionHudEntry());

    private SystemHudEntries() {
    }

    public static void init() {
    }
}
