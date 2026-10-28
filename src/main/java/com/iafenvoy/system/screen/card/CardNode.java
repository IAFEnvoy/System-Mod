package com.iafenvoy.system.screen.card;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.Nullable;

/**
 * Nodes own their serialization and presentation; the registry only resolves their codecs by ID.
 */
public interface CardNode {
    Codec<String> STYLE_CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(value -> value.map(style -> style, styles -> String.join(" ", styles)), Either::left);
    MapCodec<CardNode> CODEC = Codec.STRING.flatXmap(CardNodeTypes::decode, CardNodeTypes::encode).dispatchMap("type", CardNode::codec, codec -> codec);

    MapCodec<? extends CardNode> codec();

    String css();

    @Nullable
    Element render(CardRenderer.Render view);

    default String text(CardRenderer.Text view) {
        return "";
    }
}
