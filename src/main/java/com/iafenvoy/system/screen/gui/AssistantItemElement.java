package com.iafenvoy.system.screen.gui;

import com.sighs.apricityui.init.Document;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Item preview that appends card-owned lines (cost, odds) to the vanilla tooltip.
 */
final class AssistantItemElement extends com.sighs.apricityui.element.Item implements AssistantTooltip {
    private final List<Component> extra;

    AssistantItemElement(Document document, List<Component> extra) {
        super(document);
        this.extra = extra;
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack stack = this.getTooltipStack();
        if (stack.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(
                minecraft.level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(minecraft.level),
                minecraft.player, TooltipFlag.NORMAL));
        lines.addAll(this.extra);
        graphics.renderTooltip(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
    }
}
