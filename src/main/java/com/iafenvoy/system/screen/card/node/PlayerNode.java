package com.iafenvoy.system.screen.card.node;

import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.gui.PlayerModelElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.util.ExtraCodecs;

import java.util.UUID;

/**
 * Templates select a snapshot's UUID; the client alone resolves its live model, skin and equipment.
 */
public record PlayerNode(String player, boolean followMouse, float yaw, float pitch, float scale,
                         float offsetX, float offsetY) implements CardNode {
    public static final MapCodec<PlayerNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("player", "{{player_id}}").forGetter(PlayerNode::player),
            Codec.BOOL.lenientOptionalFieldOf("follow_mouse", false).forGetter(PlayerNode::followMouse),
            Codec.FLOAT.lenientOptionalFieldOf("yaw", 15.0F).forGetter(PlayerNode::yaw),
            Codec.FLOAT.lenientOptionalFieldOf("pitch", 0.0F).forGetter(PlayerNode::pitch),
            ExtraCodecs.POSITIVE_FLOAT.lenientOptionalFieldOf("scale", 1.0F).forGetter(PlayerNode::scale),
            Codec.FLOAT.lenientOptionalFieldOf("offset_x", 0.0F).forGetter(PlayerNode::offsetX),
            Codec.FLOAT.lenientOptionalFieldOf("offset_y", 0.0F).forGetter(PlayerNode::offsetY)
    ).apply(i, PlayerNode::new));

    @Override
    public MapCodec<PlayerNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "player_preview";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        UUID player = view.context().get(CardRenderer.binding(this.player, view.indices()), UUID.class);
        if (player == null) return null;
        Element element = new PlayerModelElement(view.document(), player, this.followMouse, this.yaw, this.pitch,
                this.scale, this.offsetX, this.offsetY);
        element.setClassName(view.css());
        return element;
    }
}
