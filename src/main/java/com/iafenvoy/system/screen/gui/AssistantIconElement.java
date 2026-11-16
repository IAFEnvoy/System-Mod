package com.iafenvoy.system.screen.gui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * A logical icon shows its own description, rather than the name of its decorative item.
 */
final class AssistantIconElement extends Element implements AssistantTooltip {
    private final List<Component> tooltip;

    AssistantIconElement(Document document, Element icon, List<Component> tooltip) {
        super(document, "div");
        this.tooltip = List.copyOf(tooltip);
        this.appendChild(icon);
    }

    @Override
    public void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.setTooltipForNextFrame(Minecraft.getInstance().font, this.tooltip, Optional.empty(), mouseX, mouseY);
    }
}
