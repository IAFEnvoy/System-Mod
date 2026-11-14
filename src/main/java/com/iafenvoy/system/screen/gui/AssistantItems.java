package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.iafenvoy.system.screen.gui.AssistantView.element;

/**
 * Typed item projections shared by cards, the current exchange and queued HUD messages.
 */
public final class AssistantItems {
    private static final int NATIVE_ICON = 20;

    private AssistantItems() {
    }

    /**
     * One preview cell of a shelf grid: the icon keeps the vanilla item tooltip and appends the card-owned lines.
     * Returns null for an empty stack, so callers can skip the slot entirely.
     */
    public static Element entry(Document document, ItemPreviewOutput preview, List<Component> tooltip) {
        return entry(document, preview, tooltip, 32);
    }

    public static Element entry(Document document, ItemPreviewOutput preview, List<Component> tooltip, int size) {
        return entry(document, preview, tooltip, size, true);
    }

    public static Element entry(Document document, ItemPreviewOutput preview, List<Component> tooltip, int size, boolean showTooltip) {
        ItemStack stack = preview.item().create();
        if (stack.isEmpty()) return null;
        Element entry = element(document, "div", "item_entry compact", "");
        Element frame = element(document, "div", "item_icon_frame", "");
        frame.setInlineStyleProperty("width", size + "px");
        frame.setInlineStyleProperty("height", size + "px");
        frame.setInlineStyleProperty("flex", "0 0 " + size + "px");
        Item icon = iconElement(document, stack, preview, tooltip, showTooltip);
        icon.setInlineStyleProperty("transform", "scale(" + (double) size / NATIVE_ICON + ")");
        // The native item is centered in its frame, so scaling must keep the same center.
        icon.setInlineStyleProperty("transform-origin", "50% 50%");
        frame.appendChild(icon);
        entry.appendChild(frame);
        return entry;
    }

    private static Item iconElement(Document document, ItemStack stack, ItemPreviewOutput preview, List<Component> tooltip, boolean showTooltip) {
        Item icon = tooltip.isEmpty() ? new Item(document) : new AssistantItemElement(document, tooltip);
        icon.setClassName("item_icon");
        icon.setAttribute("interactive", showTooltip ? "tooltip" : "none");
        icon.setDrivenState(stack, preview.amount().equals("1") ? "" : preview.amount(), false, false, Item.Source.INGREDIENT);
        return icon;
    }

    /**
     * AUI owns hit testing and clipping; vanilla owns tooltip components, advanced details and mod hooks.
     */
    public static boolean tooltip(Document document, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (document == null || document.isDisposed()) return false;
        List<Element> elements = document.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!element.isConnected() || !element.isVisible || !element.isHover)
                continue;
            if (element instanceof AssistantTooltip tooltip) {
                tooltip.renderTooltip(graphics, mouseX, mouseY);
                return true;
            }
            if (!(element instanceof Item item)) continue;
            ItemStack stack = item.getTooltipStack();
            if (stack.isEmpty()) continue;
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, stack, mouseX, mouseY);
            return true;
        }
        return false;
    }
}
