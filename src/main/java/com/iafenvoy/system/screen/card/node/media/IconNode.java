package com.iafenvoy.system.screen.card.node.media;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.CardTemplate;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.iafenvoy.system.screen.gui.AssistantIcons;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * A provider-neutral resource or item icon supplied as an IconReference context parameter.
 */
public record IconNode(String icon, List<CardTemplate.Node> tooltip) implements TextProjectingNode {
    public static final MapCodec<IconNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("icon").forGetter(IconNode::icon),
            CardTemplate.NODES.optionalFieldOf("tooltip", List.of()).forGetter(IconNode::tooltip)
    ).apply(i, IconNode::new));

    public IconNode {
        tooltip = List.copyOf(tooltip);
    }

    @Override
    public MapCodec<IconNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "quest_icon_entry";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        IconReference icon = view.context().get(CardRenderer.binding(this.icon, view.indices()), IconReference.class);
        if (icon == null) return null;
        String text = CardRenderer.text(this.tooltip, view.template(), view.context(), view.indices(), "\n");
        List<Component> tooltip = Arrays.stream(text.split("\n")).filter(line -> !line.isBlank())
                .map(line -> (Component) Component.literal(line)).toList();
        Element element = AssistantIcons.create(view.document(), icon, 32, tooltip);
        if (element != null) element.setClassName(String.join(" ", element.getClassNames()) + " " + view.css());
        return element;
    }

    @Override
    public String text(CardRenderer.Text view) {
        return "";
    }
}
