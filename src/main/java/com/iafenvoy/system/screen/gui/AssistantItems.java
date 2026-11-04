package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

import static com.iafenvoy.system.screen.gui.AssistantView.element;

/**
 * Typed item projections shared by cards, the current exchange and queued HUD messages.
 */
public final class AssistantItems {
    public record Entry(ItemPreviewOutput preview, String description) {
    }

    public record Group(String label, List<Entry> items) {
        public Group {
            items = List.copyOf(items);
        }
    }

    /**
     * A fixed slot table renders a shelf: {@code columns} tracks of {@code cell} pixels, each holding one
     * {@code icon}-pixel preview. Zero columns keeps the inline flow used by ordinary result lists.
     */
    public record Layout(int columns, int cell, int icon) {
        public static final Layout FLOW = new Layout(0, 76, 52);
        private static final int NATIVE_ICON = 20;

        public Layout {
            columns = Math.max(0, columns);
            cell = Math.max(NATIVE_ICON, cell);
            icon = Math.clamp(icon, NATIVE_ICON, cell);
        }

        public boolean table() {
            return this.columns > 0;
        }
    }

    private AssistantItems() {
    }

    /**
     * One preview cell of a shelf grid: the icon keeps the vanilla item tooltip and appends the card-owned lines.
     * Returns null for an empty stack, so callers can skip the slot entirely.
     */
    public static Element entry(Document document, ItemPreviewOutput preview, List<Component> tooltip) {
        ItemStack stack = preview.item().create();
        if (stack.isEmpty()) return null;
        Element entry = element(document, "div", "item_entry compact", "");
        Element frame = element(document, "div", "item_icon_frame", "");
        frame.appendChild(iconElement(document, stack, preview, tooltip));
        entry.appendChild(frame);
        return entry;
    }

    private static Item iconElement(Document document, ItemStack stack, ItemPreviewOutput preview, List<Component> tooltip) {
        Item icon = tooltip.isEmpty() ? new Item(document) : new AssistantItemElement(document, tooltip);
        icon.setClassName("item_icon");
        icon.setAttribute("interactive", "tooltip");
        icon.setDrivenState(stack, preview.amount().equals("1") ? "" : preview.amount(), false, false, Item.Source.INGREDIENT);
        return icon;
    }

    public static void render(Document document, Element container, List<Group> groups, boolean compact,
                              boolean showLabels, List<Component> tooltip, Layout layout) {
        if (container == null) return;
        container.setInnerHTML("");
        int count = 0;
        for (Group group : groups) {
            Element section = element(document, "div", "item_group", "");
            if (showLabels && !group.label().isBlank())
                section.appendChild(element(document, "div", "item_group_label", group.label() + ":"));
            Element row = element(document, "div", "item_row" + (layout.table() ? " table" : ""), "");
            if (layout.table()) {
                row.setInlineStyleProperty("display", "grid");
                row.setInlineStyleProperty("grid-template-columns",
                        String.join(" ", Collections.nCopies(layout.columns(), layout.cell() + "px")));
            }
            for (Entry value : group.items()) {
                ItemPreviewOutput preview = value.preview();
                ItemStack stack = preview.item().create();
                if (stack.isEmpty()) continue;
                Element entry = element(document, "div", "item_entry" + (compact ? " compact" : ""), "");
                Element frame = element(document, "div", "item_icon_frame", "");
                Item icon = iconElement(document, stack, preview, tooltip);
                if (layout.table()) {
                    frame.setInlineStyleProperty("width", layout.icon() + "px");
                    frame.setInlineStyleProperty("height", layout.icon() + "px");
                    frame.setInlineStyleProperty("flex", "0 0 " + layout.icon() + "px");
                    icon.setInlineStyleProperty("transform", "scale(" + (double) layout.icon() / Layout.NATIVE_ICON + ")");
                }
                frame.appendChild(icon);
                entry.appendChild(frame);
                if (!compact || !value.description().isBlank()) {
                    Element text = element(document, "div", "item_text", "");
                    if (!compact) text.appendChild(element(document, "div", "item_name",
                            Component.translatable("system.ui.item_amount", stack.getHoverName(), preview.amount()).getString()));
                    if (!value.description().isBlank())
                        text.appendChild(element(document, "div", "item_description", value.description()));
                    entry.appendChild(text);
                }
                row.appendChild(entry);
                count++;
            }
            if (row.children.isEmpty()) continue;
            section.appendChild(row);
            container.appendChild(section);
        }
        container.setInlineStyleProperty("display", count == 0 ? "none" : "flex");
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
