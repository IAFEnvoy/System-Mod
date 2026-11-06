package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.screen.AssistantPages;
import com.iafenvoy.system.screen.card.CardGrid;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardPageState;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.gui.AssistantView;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Groups only the supplied actions; tabs and per-group pages are local presentation state, not server queries.
 */
public record GroupedActionsNode(String actions, String group, String groupLabel, String section,
                                 String sectionLabel, CardGrid grid,
                                 List<CardTemplate.Node> children) implements CardNode {
    public static final MapCodec<GroupedActionsNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("actions", "{{result_actions}}").forGetter(GroupedActionsNode::actions),
            Codec.STRING.fieldOf("group").forGetter(GroupedActionsNode::group),
            Codec.STRING.fieldOf("group_label").forGetter(GroupedActionsNode::groupLabel),
            Codec.STRING.lenientOptionalFieldOf("section", "").forGetter(GroupedActionsNode::section),
            Codec.STRING.lenientOptionalFieldOf("section_label", "").forGetter(GroupedActionsNode::sectionLabel),
            CardGrid.CODEC.forGetter(GroupedActionsNode::grid),
            CardTemplate.Node.CODEC.listOf().lenientOptionalFieldOf("children", List.of()).forGetter(GroupedActionsNode::children)
    ).apply(i, GroupedActionsNode::new));

    public GroupedActionsNode {
        children = List.copyOf(children);
    }

    @Override
    public MapCodec<GroupedActionsNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_grouped_actions";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        List<?> source = view.context().get(CardRenderer.binding(this.actions, view.indices()), List.class);
        if (source == null || source.isEmpty()) return null;
        if (CardRenderer.enabled("simple", view.context(), List.of())) {
            ActionsNode renderer = new ActionsNode(this.actions);
            return renderer.render(new CardRenderer.Render(view.document(), renderer.css(), view.template(), view.context(), view.indices()));
        }
        List<Group> groups = this.groups(view.context(), ActionsNode.indices(view, source));
        if (groups.isEmpty()) return null;
        CardPageState state = view.context().get("local_pages", CardPageState.class);
        CardPageState pages = state == null ? new CardPageState() : state;
        String key = "grouped_actions:" + CardRenderer.binding(this.actions, view.indices()) + ":" + this.group;
        Group selected = groups.stream().filter(entry -> entry.id().equals(pages.selection(key))).findFirst().orElse(groups.getFirst());

        // Captured containers must already be native AUI elements before insertion replaces raw tags.
        Element root = Element.init(view.element("div", ""));
        Element sidebar = Element.init(AssistantView.element(view.document(), "div", "card_action_groups", ""));
        sidebar.addInternalEventListener("wheel", event -> {
            // AUI permits elastic scrolling even at zero range; keep real ancestor scrolling available.
            if (event instanceof MouseEvent mouse && sidebar.getRoute().stream().noneMatch(element ->
                    element.hasVerticalScrollRange() || mouse.shiftKey && element.hasHorizontalScrollRange()))
                event.preventDefault();
        });
        Element list = AssistantView.element(view.document(), "div", "card_action_group_list", "");
        Element results = Element.init(AssistantView.element(view.document(), "div", "card_action_group_results", ""));
        Map<String, Element> tabs = new LinkedHashMap<>();
        String previousSection = null;
        for (Group entry : groups) {
            if (!entry.section().equals(previousSection) && !LegacyText.plain(entry.sectionLabel()).isBlank())
                list.appendChild(AssistantView.element(view.document(), "div", "card_action_group_section", entry.sectionLabel()));
            previousSection = entry.section();
            Element tab = Element.init(AssistantView.element(view.document(), "button", "card_action_group", ""));
            tab.setAttribute("type", "button");
            tab.appendChild(AssistantView.element(view.document(), "span", "card_control_label", entry.label()));
            tab.appendChild(AssistantView.element(view.document(), "span", "card_action_group_count", Integer.toString(entry.indices().size())));
            tabs.put(entry.id(), tab);
            tab.addEventListener("click", _ -> {
                if (!view.interactive() || !root.isConnected() || entry.id().equals(pages.selection(key))) return;
                this.select(view, results, source, entry, tabs, pages, key);
                root.getLayout().invalidateLayoutSubtree();
                AssistantPages.commit(view.document());
            });
            list.appendChild(tab);
        }
        sidebar.appendChild(list);
        root.appendChild(sidebar);
        root.appendChild(results);
        this.select(view, results, source, selected, tabs, pages, key);
        return root;
    }

    private void select(CardRenderer.Render view, Element results, List<?> source, Group selected,
                        Map<String, Element> tabs, CardPageState pages, String key) {
        pages.selection(key, selected.id());
        tabs.forEach((id, tab) -> {
            boolean active = id.equals(selected.id());
            tab.setClassName("card_action_group" + (active ? " selected" : ""));
            tab.setAttribute("aria-pressed", Boolean.toString(active));
        });
        results.setInnerHTML("");
        results.appendChild(this.grid.render(view, key + ":" + selected.id(), selected.indices(), "offer_grid card_action_grid",
                (parent, index) -> this.renderCell(view, parent, source, index)));
    }

    private void renderCell(CardRenderer.Render view, Element grid, List<?> source, int index) {
        // Use the shop/lottery shelf structure; rich text is a block inside a cell, never a flex button label.
        Element cell = AssistantView.element(view.document(), "div", "offer_cell card_action_cell", "");
        cell.setInlineStyleProperty("height", this.grid.cell() + "px");
        if (this.children.isEmpty())
            cell.appendChild(AssistantView.element(view.document(), "div", "offer_cell_label", view.template().label(view.context(), index)));
        else CardRenderer.render(view.document(), cell, this.children, view.template(), view.context(), List.of(index));
        ActionsNode.bind(view, cell, (CardButton) source.get(index), index);
        grid.appendChild(cell);
    }

    private List<Group> groups(CardContext context, List<Integer> indices) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (int index : indices) {
            List<Integer> loop = List.of(index);
            String label = CardRenderer.value(this.groupLabel, context, loop);
            if (LegacyText.plain(label).isBlank()) label = AssistantView.tr("system.ui.card_ungrouped");
            String id = CardRenderer.rawValue(this.group, context, loop);
            if (id.isBlank()) id = LegacyText.plain(label);
            Group entry = groups.get(id);
            if (entry == null) {
                entry = new Group(id, label, CardRenderer.rawValue(this.section, context, loop),
                        CardRenderer.value(this.sectionLabel, context, loop), new ArrayList<>());
                groups.put(id, entry);
            }
            // Preserve original callback indices and numbering even after filtering or changing pages.
            entry.indices().add(index);
        }
        return List.copyOf(groups.values());
    }

    @Override
    public String text(CardRenderer.Text view) {
        List<?> source = view.context().get(CardRenderer.binding(this.actions, view.indices()), List.class);
        if (source == null) return "";
        List<Integer> indices = IntStream.range(0, source.size()).filter(index -> source.get(index) instanceof CardButton).boxed().toList();
        List<String> lines = new ArrayList<>();
        for (Group entry : this.groups(view.context(), indices)) {
            lines.add(entry.label());
            for (int index : entry.indices()) lines.add(view.template().label(view.context(), index));
        }
        return String.join(view.separator(), lines);
    }

    private record Group(String id, String label, String section, String sectionLabel, List<Integer> indices) {
    }
}
