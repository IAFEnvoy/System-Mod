package com.iafenvoy.system.screen;

import com.iafenvoy.saturn.screen.hud.HudAnchor;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A separate left-hand HUD can be moved and hidden using Saturn's existing editor.
 */
public final class DisplayCardsHudEntry extends AuiHudEntry {
    public static final String TEMPLATE = "system/assistant-cards.html";

    public DisplayCardsHudEntry() {
        super("system.cards", new Layout(TEMPLATE, "card_display", 360, 240, 0.38, 0.72));
    }

    @Override
    public String displayName() {
        return Component.translatable("system.hud.cards").getString();
    }

    @Override
    protected String previewKey() {
        return "system.ui.card_display_preview";
    }

    public double slideOffset(Document document) {
        int windowWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        boolean right = switch (this.anchor()) {
            case RIGHT_TOP, RIGHT_CENTER, RIGHT_BOTTOM -> true;
            case LEFT_TOP, LEFT_CENTER, LEFT_BOTTOM -> false;
            case CENTER_TOP, CENTER_BOTTOM -> this.x() + this.layoutWidth() / 2.0 > windowWidth / 2.0;
        };
        // Include a shadow margin and convert the off-screen travel to the owning document's CSS pixels.
        double distance = right ? windowWidth - this.x() + 16 : -this.x() - this.layoutWidth() - 16;
        return document.guiToDocumentPosition(new Position(distance, 0)).x
                - document.guiToDocumentPosition(Position.ZERO).x;
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.LEFT_TOP;
    }

    @Override
    public int defaultOffsetX() {
        return 4;
    }

    @Override
    public int defaultOffsetY() {
        return Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() * 0.22F);
    }
}
