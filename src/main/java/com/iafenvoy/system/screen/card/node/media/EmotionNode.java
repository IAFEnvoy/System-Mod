package com.iafenvoy.system.screen.card.node.media;

import com.iafenvoy.system.screen.card.CardNode;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.iafenvoy.system.screen.emotion.AssistantEmotions;
import com.iafenvoy.system.screen.emotion.EmotionProfiles;
import com.iafenvoy.system.screen.emotion.EmotionStateMachine;
import com.iafenvoy.system.screen.gui.EmotionElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sighs.apricityui.init.Element;
import net.minecraft.resources.Identifier;

/**
 * Shared card state survives redraws; state and profile remain resource-pack-controlled parameters.
 */
public record EmotionNode(String profile, String state, int size) implements CardNode {
    public static final MapCodec<EmotionNode> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.lenientOptionalFieldOf("profile", "system:panda").forGetter(EmotionNode::profile),
            Codec.STRING.lenientOptionalFieldOf("state", "{{emotion}}").forGetter(EmotionNode::state),
            Codec.INT.lenientOptionalFieldOf("size", 72).forGetter(EmotionNode::size)
    ).apply(i, EmotionNode::new));

    @Override
    public MapCodec<EmotionNode> codec() {
        return CODEC;
    }

    @Override
    public String css() {
        return "card_emotion";
    }

    @Override
    public Element render(CardRenderer.Render view) {
        Identifier profile = Identifier.tryParse(CardRenderer.rawValue(this.profile, view.context(), view.indices()));
        if (profile == null) profile = EmotionProfiles.DEFAULT;
        String state = CardRenderer.rawValue(this.state, view.context(), view.indices());
        EmotionStateMachine machine = view.ui().emotion(view.stateKey(), profile, state);
        AssistantEmotions.attach(view.reference().id(), machine);
        EmotionElement element = new EmotionElement(view.document(), view.reference().id(), machine);
        element.setClassName(view.css());
        element.setInlineStyleProperty("width", Math.max(1, this.size) + "px");
        element.setInlineStyleProperty("height", Math.max(1, this.size) + "px");
        return element;
    }
}
