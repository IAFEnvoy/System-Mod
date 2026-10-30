package com.iafenvoy.system.screen.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Custom card elements share the same post-render tooltip pass as vanilla item previews.
 */
public interface AssistantTooltip {
    void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY);
}
