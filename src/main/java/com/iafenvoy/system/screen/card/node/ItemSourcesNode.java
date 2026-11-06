package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.itemsource.ItemSource;
import com.iafenvoy.system.itemsource.ItemSourceSlot;
import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardPageState;
import com.iafenvoy.system.screen.card.CardPagination;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Local category tabs and paging share the card state; only the active page creates item previews and tooltips.
 */
public record ItemSourcesNode(String sources, int recipePageSize, int lootPageSize, int tagPageSize) implements CardNode {
    public static final MapCodec<ItemSourcesNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("sources", "{{sources}}").forGetter(ItemSourcesNode::sources),
            Codec.INT.lenientOptionalFieldOf("recipe_page_size", 4).forGetter(ItemSourcesNode::recipePageSize),
            Codec.INT.lenientOptionalFieldOf("loot_page_size", 12).forGetter(ItemSourcesNode::lootPageSize),
            Codec.INT.lenientOptionalFieldOf("tag_page_size", 24).forGetter(ItemSourcesNode::tagPageSize)
    ).apply(i, ItemSourcesNode::new));
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
    public MapCodec<ItemSourcesNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "item_sources";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        String key = CardRenderer.binding(this.sources, view.indices());
        List<ItemSource> entries = entries(view.context().get(key, List.class));
        if (entries.isEmpty()) return null;
        Map<Category, List<ItemSource>> groups = new EnumMap<>(Category.class);
        for (Category category : Category.values()) groups.put(category, new ArrayList<>());
        for (ItemSource entry : entries) groups.get(Category.of(entry)).add(entry);
        CardPageState state = view.context().get("local_pages", CardPageState.class);
        CardPageState pages = state == null ? new CardPageState() : state;
        String stateKey = "item_sources:" + key;
        Category selected = Arrays.stream(Category.values()).filter(category -> !groups.get(category).isEmpty()
                && category.name().equals(pages.selection(stateKey))).findFirst()
                .orElseGet(() -> Arrays.stream(Category.values()).filter(category -> !groups.get(category).isEmpty()).findFirst().orElseThrow());

        // Captured elements must be initialized before AUI attaches and replaces raw tags.
        Element root = Element.init(view.element("div", ""));
        Element tabs = AssistantView.element(view.document(), "div", "source_tabs", "");
        Element content = Element.init(AssistantView.element(view.document(), "div", "source_content", ""));
        Map<Category, Element> buttons = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            Element button = Element.init(AssistantView.element(view.document(), "button", "card_action source_tab", ""));
            button.setAttribute("type", "button");
            button.setDisabled(groups.get(category).isEmpty());
            button.appendChild(AssistantView.element(view.document(), "span", "", AssistantView.tr(category.label)));
            button.appendChild(AssistantView.element(view.document(), "span", "source_tab_count", Integer.toString(groups.get(category).size())));
            buttons.put(category, button);
            button.addEventListener("click", event -> {
                event.stopPropagation();
                if (groups.get(category).isEmpty() || !view.interactive() || !root.isConnected()
                        || category.name().equals(pages.selection(stateKey))) return;
                this.select(view, content, groups, category, buttons, pages, stateKey);
                Element scroll = view.document().getElementById("notification_scroll");
                if (scroll != null) scroll.scrollTop = scroll.targetScrollTop = 0;
                root.getLayout().invalidateLayoutSubtree();
                AssistantPages.commit(view.document());
            });
            tabs.appendChild(button);
        }
        root.appendChild(tabs);
        root.appendChild(content);
        this.select(view, content, groups, selected, buttons, pages, stateKey);
        return root;
    }

    private void select(CardRenderer.Render view, Element content, Map<Category, List<ItemSource>> groups, Category selected,
                        Map<Category, Element> buttons, CardPageState pages, String key) {
        pages.selection(key, selected.name());
        buttons.forEach((category, button) -> {
            boolean active = category == selected;
            button.setClassName("card_action source_tab" + (active ? " selected" : ""));
            button.setAttribute("aria-pressed", Boolean.toString(active));
        });
        content.setInnerHTML("");
        if (selected == Category.LOOT) hint(view, content, AssistantView.tr("system.ui.source_loot_hint"));
        if (selected == Category.TAG) hint(view, content, AssistantView.tr("system.ui.source_tag_hint"));
        boolean full = CardRenderer.enabled("full", view.context(), List.of());
        int pageSize = switch (selected) {
            case RECIPE -> full ? this.recipePageSize : Math.min(this.recipePageSize, 2);
            case LOOT -> this.lootPageSize;
            case TAG -> this.tagPageSize;
        };
        content.appendChild(CardPagination.render(view, key + ":" + selected.name(), groups.get(selected), pageSize, true, (parent, page) -> {
            parent.setClassName(selected.css);
            if (selected == Category.RECIPE)
                parent.setInlineStyleProperty("grid-template-columns", full ? "1fr 1fr" : "1fr");
            for (ItemSource source : page) {
                Element entry = AssistantView.element(view.document(), "div", "source_entry", "");
                switch (selected) {
                    case RECIPE -> renderSource(view, entry, source);
                    case LOOT -> {
                        entry.appendChild(AssistantView.element(view.document(), "div", "card_context source_id", source.id()));
                        if (!source.notes().isEmpty()) hint(view, entry, String.join(" · ", source.notes().stream().map(ItemSourcesNode::note).toList()));
                    }
                    case TAG -> AssistantView.text(entry, "#" + source.id());
                }
                parent.appendChild(entry);
            }
        }));
    }

    private static List<ItemSource> entries(List<?> values) {
        return values == null ? List.of() : values.stream().filter(ItemSource.class::isInstance).map(ItemSource.class::cast).toList();
    }

    private static void renderSource(CardRenderer.Render view, Element parent, ItemSource source) {
        Element header = AssistantView.element(view.document(), "div", "source_entry_header", "");
        header.appendChild(AssistantView.element(view.document(), "div", "source_heading", heading(source)));
        Element stations = AssistantView.element(view.document(), "div", "source_stations", "");
        for (String raw : source.station()) {
            Identifier id = Identifier.tryParse(raw);
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

    @Override
    public String text(CardRenderer.Text view) {
        List<ItemSource> sources = entries(view.context().get(CardRenderer.binding(this.sources, view.indices()), List.class));
        return String.join("\n", sources.stream().map(source -> heading(source) + " · " + source.id()
                + (source.inputs().isEmpty() ? "" : "\n" + AssistantView.tr("system.ui.source_inputs") + ": " + items(source.inputs()))
                + (source.outputs().isEmpty() ? "" : "\n" + AssistantView.tr("system.ui.source_outputs") + ": " + items(source.outputs()))).toList());
    }

    private static String items(List<ItemSourceSlot> slots) {
        return String.join(" + ", slots.stream().filter(slot -> !slot.items().isEmpty())
                .map(slot -> LegacyText.formatted(slot.items().getFirst().create().getHoverName()) + " × " + slot.count()).toList());
    }

    private enum Category {
        RECIPE("system.ui.source_category_recipe", "source_recipe_list"),
        LOOT("system.ui.source_category_loot", "source_loot_list"),
        TAG("system.ui.source_category_tag", "source_tag_list");

        private final String label;
        private final String css;

        Category(String label, String css) {
            this.label = label;
            this.css = css;
        }

        private static Category of(ItemSource source) {
            return switch (source.kind()) {
                case "loot" -> LOOT;
                case "tag" -> TAG;
                // Custom providers keep their full source presentation in the recipe category.
                default -> RECIPE;
            };
        }
    }
}
