package com.iafenvoy.system.screen.card.node.integration;

import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.itemsource.ItemSource;
import com.iafenvoy.system.itemsource.ItemSourceSlot;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.iafenvoy.system.util.ItemStackTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A single typed recipe presentation; lists, categories and paging belong to layouts.
 */
public record RecipeNode(String recipe) implements TextProjectingNode {
    public static final MapCodec<RecipeNode> CODEC = Codec.STRING.fieldOf("recipe").xmap(RecipeNode::new, RecipeNode::recipe);
    private static final Map<String, String> RECIPE_NAMES = Map.of(
            "minecraft:crafting", "crafting",
            "minecraft:smelting", "smelting",
            "minecraft:blasting", "blasting",
            "minecraft:smoking", "smoking",
            "minecraft:campfire_cooking", "campfire_cooking",
            "minecraft:stonecutting", "stonecutting",
            "minecraft:smithing", "smithing"
    );


    @Override
    public MapCodec<RecipeNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "source_entry";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        ItemSource source = view.context().get(CardRenderer.binding(this.recipe, view.indices()), ItemSource.class);
        if (source == null) return null;
        Element root = view.element("div", "");
        renderSource(view, root, source);
        return root;
    }

    @Override
    public String text(CardRenderer.Text view) {
        ItemSource source = view.context().get(CardRenderer.binding(this.recipe, view.indices()), ItemSource.class);
        return source == null ? "" : heading(source) + " · " + source.id()
                + (source.inputs().isEmpty() ? "" : "\n" + AssistantView.tr("system.ui.source_inputs") + ": " + items(source.inputs()))
                + (source.outputs().isEmpty() ? "" : "\n" + AssistantView.tr("system.ui.source_outputs") + ": " + items(source.outputs()));
    }

    private static void renderSource(CardRenderer.Render view, Element parent, ItemSource source) {
        Element header = AssistantView.element(view.document(), "div", "source_entry_header", "");
        header.appendChild(AssistantView.element(view.document(), "div", "source_heading", heading(source)));
        Element stations = AssistantView.element(view.document(), "div", "source_stations", "");
        for (String raw : source.station()) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) continue;
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                Element preview = AssistantItems.entry(view.document(), ItemPreviewOutput.of(new ItemStackTemplate(item)),
                        List.of(Component.translatable("system.ui.source_station").withStyle(ChatFormatting.GRAY)));
                if (preview != null) stations.appendChild(preview);
            });
        }
        if (!stations.children.isEmpty()) header.appendChild(stations);
        parent.appendChild(header);
        parent.appendChild(AssistantView.element(view.document(), "div", "card_context source_id", source.id()));
        if (!source.inputs().isEmpty()) {
            Element recipe = AssistantView.element(view.document(), "div", "source_recipe", "");
            // Pad vanilla patterns to a 3x3 table without collapsing empty cells or repeated ingredients.
            int columns = source.width() > 0 ? source.width() : Math.min(3, source.inputs().size());
            List<ItemSourceSlot> inputs = source.inputs();
            if (source.width() > 0 && source.width() <= 3 && inputs.size() <= source.width() * 3) {
                List<ItemSourceSlot> padded = new ArrayList<>();
                ItemSourceSlot empty = new ItemSourceSlot(List.of(), 1);
                for (int row = 0; row < 3; row++)
                    for (int column = 0; column < 3; column++) {
                        int index = row * source.width() + column;
                        padded.add(column < source.width() && index < inputs.size() ? inputs.get(index) : empty);
                    }
                inputs = padded;
                columns = 3;
            }
            recipe.appendChild(section(view, "system.ui.source_inputs", inputs, columns));
            recipe.appendChild(AssistantView.element(view.document(), "div", "source_arrow", "→"));
            recipe.appendChild(section(view, "system.ui.source_outputs", source.outputs(), 0));
            parent.appendChild(recipe);
        } else if (!source.outputs().isEmpty()) {
            parent.appendChild(section(view, "system.ui.source_outputs", source.outputs(), 0));
            if (source.kind().equals("recipe")) hint(view, parent, AssistantView.tr("system.ui.source_unknown_inputs"));
        }
        for (String note : source.notes()) hint(view, parent, note(note));
    }

    private static Element section(CardRenderer.Render view, String label, List<ItemSourceSlot> slots, int columns) {
        Element section = AssistantView.element(view.document(), "div", "source_section", "");
        section.appendChild(AssistantView.element(view.document(), "div", "source_label", AssistantView.tr(label)));
        Element grid = AssistantView.element(view.document(), "div", "source_slots", "");
        if (columns > 0) {
            grid.setInlineStyleProperty("display", "grid");
            grid.setInlineStyleProperty("grid-template-columns", String.join(" ", Collections.nCopies(columns, "36px")));
        }
        for (ItemSourceSlot slot : slots) {
            Element cell = AssistantView.element(view.document(), "div", "source_slot", "");
            if (!slot.items().isEmpty()) {
                List<Component> tooltip = new ArrayList<>();
                if (slot.items().size() > 1) {
                    tooltip.add(Component.translatable("system.ui.source_alternatives").withStyle(ChatFormatting.GRAY));
                    for (ItemStackTemplate item : slot.items())
                        tooltip.add(item.create().getHoverName().copy().withStyle(ChatFormatting.GRAY));
                }
                Element preview = AssistantItems.entry(view.document(),
                        new ItemPreviewOutput(slot.items().getFirst(), Integer.toString(slot.count())), tooltip);
                if (preview != null) cell.appendChild(preview);
                if (slot.items().size() > 1)
                    cell.appendChild(AssistantView.element(view.document(), "span", "source_alternatives", "+" + (slot.items().size() - 1)));
            }
            grid.appendChild(cell);
        }
        section.appendChild(grid);
        return section;
    }

    private static void hint(CardRenderer.Render view, Element parent, String value) {
        parent.appendChild(AssistantView.element(view.document(), "div", "card_context source_hint", value));
    }

    private static String heading(ItemSource source) {
        if (!source.kind().equals("recipe")) return switch (source.kind()) {
            case "loot" -> AssistantView.tr("system.ui.source_loot");
            case "tag" -> AssistantView.tr("system.ui.source_tag");
            default -> source.kind();
        };
        String type = RECIPE_NAMES.get(source.type());
        return type == null ? AssistantView.tr("system.ui.source_recipe") + " · " + source.type()
                : AssistantView.tr("system.ui.source_" + type);
    }

    private static String note(String value) {
        if (value.equals("requires_fuel")) return AssistantView.tr("system.ui.source_fuel");
        for (String prefix : List.of("weight", "quality", "tag"))
            if (value.startsWith(prefix + " "))
                return Component.translatable("system.ui.source_" + prefix + "_note", value.substring(prefix.length() + 1)).getString();
        return value;
    }

    private static String items(List<ItemSourceSlot> slots) {
        return String.join(" + ", slots.stream().filter(slot -> !slot.items().isEmpty())
                .map(slot -> LegacyText.formatted(slot.items().getFirst().create().getHoverName()) + " × " + slot.count()).toList());
    }

}
