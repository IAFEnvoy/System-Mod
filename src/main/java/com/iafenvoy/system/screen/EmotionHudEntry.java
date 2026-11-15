package com.iafenvoy.system.screen;

import com.iafenvoy.saturn.screen.hud.HudAnchor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The editor owns placement and persistent visibility; emotion test state remains connection-local. */
public final class EmotionHudEntry extends AuiHudEntry {
    public static final String TEMPLATE = "system/assistant-emotion.html";

    public EmotionHudEntry() {
        super("system.emotion", new Layout(TEMPLATE, "emotion_hud", 224, 224, 1, 1));
    }

    @Override
    public String displayName() {
        return Component.translatable("system.hud.emotion").getString();
    }

    @Override
    protected String previewKey() {
        return "system.ui.emotion_preview";
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!AssistantEmotionHud.mounted() || !this.visible()) super.render(graphics, deltaTracker);
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.LEFT_TOP;
    }

    @Override
    public int defaultOffsetX() {
        return 12;
    }

    @Override
    public int defaultOffsetY() {
        return 12;
    }
}
