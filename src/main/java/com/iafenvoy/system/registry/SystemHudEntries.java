package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.screen.hud.HudManager;
import com.iafenvoy.system.screen.DisplayCardsHudEntry;
import com.iafenvoy.system.screen.NotificationHudEntry;

/**
 * Initialized only by the physical client entrypoint; Saturn owns the shared HUD editor and layout file.
 */
public final class SystemHudEntries {
    public static final NotificationHudEntry NOTIFICATIONS = HudManager.register(new NotificationHudEntry());
    public static final DisplayCardsHudEntry DISPLAY_CARDS = HudManager.register(new DisplayCardsHudEntry());

    private SystemHudEntries() {
    }

    public static void init() {
    }
}
