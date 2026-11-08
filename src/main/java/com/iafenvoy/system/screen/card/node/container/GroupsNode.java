package com.iafenvoy.system.screen.card.node.container;

import com.iafenvoy.system.data.card.CardContext;
import com.iafenvoy.system.screen.AssistantGeometry;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.card.CollectionNode;
import com.iafenvoy.system.screen.card.TextProjectingNode;
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

/**
 * Group selection passes an index subset to descendants; it does not own a grid or business action.
 */
public record GroupsNode(String source, String filter, String group, String groupLabel, String section,
                         String sectionLabel, String orientation, List<Option> options,
                         List<CardTemplate.Node> children) implements CollectionNode, TextProjectingNode {
    public record Option(String id, String label) {
        public static final Codec<Option> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Option::id), Codec.STRING.fieldOf("label").forGetter(Option::label)
        ).apply(i, Option::new));
    }

    public static final MapCodec<GroupsNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("source").forGetter(GroupsNode::source),
            Codec.STRING.lenientOptionalFieldOf("filter", "").forGetter(GroupsNode::filter),
            Codec.STRING.fieldOf("group").forGetter(GroupsNode::group),
            Codec.STRING.lenientOptionalFieldOf("group_label", "").forGetter(GroupsNode::groupLabel),
            Codec.STRING.lenientOptionalFieldOf("section", "").forGetter(GroupsNode::section),
            Codec.STRING.lenientOptionalFieldOf("section_label", "").forGetter(GroupsNode::sectionLabel),
            Codec.STRING.lenientOptionalFieldOf("orientation", "vertical").forGetter(GroupsNode::orientation),
            Option.CODEC.listOf().lenientOptionalFieldOf("options", List.of()).forGetter(GroupsNode::options),
            CardTemplate.NODES.fieldOf("children").forGetter(GroupsNode::children)
    ).apply(i, GroupsNode::new));

    public GroupsNode {
        children = List.copyOf(children);
        options = List.copyOf(options);
    }

    @Override
    public MapCodec<GroupsNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_grouped_actions";
    }

    private record Group(String id, String label, String section, String sectionLabel, List<Integer> indices) {
    }

    private List<Group> groups(CardContext context, List<Integer> outer, List<Integer> indices) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Option option : this.options)
            groups.put(option.id, new Group(option.id,
                    CardRenderer.value(option.label, context, outer), "", "", new ArrayList<>()));
        for (int index : indices) {
            List<Integer> loop = CardRenderer.loop(outer, index);
            String id = CardRenderer.rawValue(this.group, context, loop);
            String label = CardRenderer.value(this.groupLabel, context, loop);
            if (LegacyText.plain(label).isBlank()) label = AssistantView.tr("system.ui.card_ungrouped");
            if (id.isBlank()) id = LegacyText.plain(label);
            Group entry = groups.get(id);
            if (entry == null) {
                entry = new Group(id, label, CardRenderer.rawValue(this.section, context, loop),
                        CardRenderer.value(this.sectionLabel, context, loop), new ArrayList<>());
                groups.put(id, entry);
            }
            entry.indices.add(index);
        }
        return List.copyOf(groups.values());
    }

    @Override
    public Element render(CardRenderer.Render view) {
        List<Group> groups = this.groups(view.context(), view.indices(), this.indices(view));
        Group selected = groups.stream().filter(g -> !g.indices.isEmpty() && g.id.equals(view.ui().selection(view.stateKey())))
                .findFirst().orElseGet(() -> groups.stream().filter(g -> !g.indices.isEmpty()).findFirst().orElse(null));
        if (selected == null) return null;
        boolean horizontal = "horizontal".equals(this.orientation);
        Element root = Element.init(view.element("div", ""));
        if (horizontal) root.setClassName(view.css() + " card_groups_horizontal");
        Element sidebar = Element.init(AssistantView.element(view.document(), "div", horizontal ? "source_tabs" : "card_action_groups", ""));
        sidebar.addInternalEventListener("wheel", event -> {
            if (event instanceof MouseEvent mouse && sidebar.getRoute().stream().noneMatch(e ->
                    AssistantGeometry.hasScrollRange(e, false) || mouse.shiftKey && AssistantGeometry.hasScrollRange(e, true)))
                event.preventDefault();
        });
        Element list = horizontal ? sidebar : AssistantView.element(view.document(), "div", "card_action_group_list", "");
        Element content = Element.init(AssistantView.element(view.document(), "div", "card_action_group_results", ""));
        Map<String, Element> buttons = new LinkedHashMap<>();
        String previousSection = null;
        for (Group entry : groups) {
            if (!entry.section.equals(previousSection) && !LegacyText.plain(entry.sectionLabel).isBlank())
                list.appendChild(AssistantView.element(view.document(), "div", "card_action_group_section", entry.sectionLabel));
            previousSection = entry.section;
            Element tab = Element.init(AssistantView.element(view.document(), "button", horizontal ? "card_action source_tab" : "card_action_group", ""));
            tab.setAttribute("type", "button");
            tab.setDisabled(entry.indices.isEmpty());
            tab.appendChild(AssistantView.element(view.document(), "span", "card_control_label", entry.label));
            tab.appendChild(AssistantView.element(view.document(), "span", "card_action_group_count", Integer.toString(entry.indices.size())));
            buttons.put(entry.id, tab);
            list.appendChild(tab);
            tab.addEventListener("click", event -> {
                event.stopPropagation();
                if (!view.interactive() || !root.isConnected() || entry.indices.isEmpty()
                        || entry.id.equals(view.ui().selection(view.stateKey()))) return;
                this.select(view, content, entry, buttons);
                root.getLayout().invalidateLayoutSubtree();
            });
        }
        if (!horizontal) sidebar.appendChild(list);
        root.appendChild(sidebar);
        root.appendChild(content);
        this.select(view, content, selected, buttons);
        return root;
    }

    private void select(CardRenderer.Render view, Element parent, Group group, Map<String, Element> buttons) {
        view.ui().selection(view.stateKey(), group.id);
        buttons.forEach((id, tab) -> {
            boolean selected = id.equals(group.id);
            tab.setAttribute("aria-pressed", Boolean.toString(selected));
            tab.setClassName(("horizontal".equals(this.orientation) ? "card_action source_tab" : "card_action_group") + (selected ? " selected" : ""));
        });
        parent.setInnerHTML("");
        this.renderChildren(view.select(CardRenderer.binding(this.source, view.indices()), group.indices, group.id), parent);
    }

    @Override
    public String text(CardRenderer.Text view) {
        // Export every group once; the active tab and page never hide content from text projection.
        return this.textChildren(view, view.separator());
    }
}
