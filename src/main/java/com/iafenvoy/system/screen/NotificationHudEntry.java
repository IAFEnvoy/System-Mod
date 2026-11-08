package com.iafenvoy.system.screen;

import com.iafenvoy.saturn.screen.hud.HudAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Notification placement stays independent of the queue, expansion animation and narration.
 */
public final class NotificationHudEntry extends AuiHudEntry {
    public static final String TEMPLATE = "system/assistant-message.html";

    public NotificationHudEntry() {
        super("system.notification", new Layout(TEMPLATE, "workspace", 840, 220, 0.94, 0.64));
    }

    @Override
    public String displayName() {
        return Component.translatable("system.hud.notifications").getString();
    }

    @Override
    protected String previewKey() {
        return "system.ui.notification_preview";
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.CENTER_BOTTOM;
    }

    @Override
    public int defaultOffsetX() {
        return 0;
    }

    @Override
    public int defaultOffsetY() {
        return -Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() * 0.06F);
    }
}
