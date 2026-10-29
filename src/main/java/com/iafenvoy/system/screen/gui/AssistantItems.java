package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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

    private AssistantItems() {
    }

    public static void render(Document document, Element container, List<Group> groups, boolean compact) {
        render(document, container, groups, compact, true);
    }

    public static void render(Document document, Element container, List<Group> groups, boolean compact, boolean showLabels) {
        if (container == null) return;
        container.setInnerHTML("");
        int count = 0;
        for (Group group : groups) {
            Element section = element(document, "div", "item_group", "");
            if (showLabels && !group.label().isBlank())
                section.appendChild(element(document, "div", "item_group_label", group.label() + ":"));
            Element row = element(document, "div", "item_row", "");
            for (Entry value : group.items()) {
                ItemPreviewOutput preview = value.preview();
                ItemStack stack = preview.item().create();
                if (stack.isEmpty()) continue;
                Element entry = element(document, "div", "item_entry" + (compact ? " compact" : ""), "");
                Element frame = element(document, "div", "item_icon_frame", "");
                Item icon = new Item(document);
                icon.setClassName("item_icon");
                icon.setAttribute("interactive", "tooltip");
                icon.setDrivenState(stack, preview.amount().equals("1") ? "" : preview.amount(), false, false, Item.Source.INGREDIENT);
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
    public static boolean tooltip(Document document, GuiGraphics graphics, int mouseX, int mouseY) {
        if (document == null || document.isDisposed()) return false;
        List<Element> elements = document.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            if (!(elements.get(index) instanceof Item item) || !item.isConnected() || !item.isVisible || !item.isHover)
                continue;
            ItemStack stack = item.getTooltipStack();
            if (stack.isEmpty()) continue;
            graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
            return true;
        }
        return false;
    }
}
