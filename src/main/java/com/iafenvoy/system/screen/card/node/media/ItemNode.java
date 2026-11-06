package com.iafenvoy.system.screen.card.node.media;

import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.iafenvoy.system.screen.gui.AssistantItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * One item renderer; list labels, grouping and paging belong to ordinary nodes.
 */
public record ItemNode(String item, int size, List<CardTemplate.Node> tooltip) implements TextProjectingNode {
    public static final MapCodec<ItemNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(ItemNode::item),
            Codec.INT.lenientOptionalFieldOf("size", 32).forGetter(ItemNode::size),
            CardTemplate.NODES.lenientOptionalFieldOf("tooltip", List.of()).forGetter(ItemNode::tooltip)
    ).apply(i, ItemNode::new));

    public ItemNode {
        tooltip = List.copyOf(tooltip);
    }

    @Override
    public MapCodec<ItemNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_item";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        ItemPreviewOutput item = view.context().get(CardRenderer.binding(this.item, view.indices()), ItemPreviewOutput.class);
        if (item == null) return null;
        String text = CardRenderer.text(this.tooltip, view.template(), view.context(), view.indices(), "\n");
        List<Component> tooltip = text.isBlank() ? List.of() : Arrays.stream(text.split("\n"))
                .filter(line -> !line.isBlank()).map(line -> (Component) Component.literal(line).withStyle(ChatFormatting.GRAY)).toList();
        Element element = AssistantItems.entry(view.document(), item, tooltip, Math.max(1, this.size));
        if (element != null) element.setClassName(String.join(" ", element.getClassNames()) + " " + view.css());
        return element;
    }

    @Override
    public String text(CardRenderer.Text view) {
        ItemPreviewOutput item = view.context().get(CardRenderer.binding(this.item, view.indices()), ItemPreviewOutput.class);
        return item == null ? "" : item.item().create().getHoverName().getString() + " × " + item.amount();
    }
}
