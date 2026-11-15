package com.iafenvoy.system.screen.card.node.media;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.card.TextProjectingNode;
import com.iafenvoy.system.screen.gui.AssistantIcons;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;

/**
 * A provider-neutral resource or item icon supplied as an IconReference context parameter.
 */
public record IconNode(String icon) implements TextProjectingNode {
    public static final MapCodec<IconNode> CODEC = Codec.STRING.fieldOf("icon").xmap(IconNode::new, IconNode::icon);

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
        return icon == null ? null : AssistantIcons.create(view.document(), icon, 32);
    }

    @Override
    public String text(CardRenderer.Text view) {
        return "";
    }
}
