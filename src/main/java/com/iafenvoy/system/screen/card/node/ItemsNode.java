package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;

import java.util.List;
import java.util.Optional;

public record ItemsNode(String items, String label, List<CardTemplate.Node> description,
                        Optional<Boolean> compact, boolean showLabels) implements CardNode {
    public static final MapCodec<ItemsNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("items").forGetter(ItemsNode::items),
            Codec.STRING.optionalFieldOf("label", "").forGetter(ItemsNode::label),
            Codec.lazyInitialized(() -> CardTemplate.Node.CODEC).listOf().optionalFieldOf("description", List.of()).forGetter(ItemsNode::description),
            Codec.BOOL.optionalFieldOf("compact").forGetter(ItemsNode::compact),
            Codec.BOOL.optionalFieldOf("show_labels", true).forGetter(ItemsNode::showLabels)
    ).apply(i, ItemsNode::new));

    public ItemsNode {
        description = List.copyOf(description);
    }

    @Override
    public MapCodec<ItemsNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "item_groups";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        List<?> source = view.context().get(CardRenderer.binding(this.items, view.indices()), List.class);
        if (source == null) return null;
        String description = CardRenderer.text(this.description, view.template(), view.context(), view.indices(), "\n");
        List<AssistantItems.Entry> entries = source.stream().filter(ItemPreviewOutput.class::isInstance).map(ItemPreviewOutput.class::cast)
                .map(item -> new AssistantItems.Entry(item, description)).toList();
        if (entries.isEmpty()) return null;
        List<AssistantItems.Group> groups = List.of(new AssistantItems.Group(CardRenderer.value(this.label, view.context(), view.indices()), entries));
        Element element = view.element("div", "");
        boolean compact = this.compact.orElse(CardRenderer.enabled("notification", view.context(), List.of()));
        AssistantItems.render(view.document(), element, groups, compact, this.showLabels);
        return element.children.isEmpty() ? null : element;
    }
}
