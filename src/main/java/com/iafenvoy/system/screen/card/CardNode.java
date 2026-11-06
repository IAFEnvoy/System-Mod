package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.sighs.apricityui.init.Element;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Nodes own their serialization and presentation; the registry only resolves their codecs by ID.
 */
public interface CardNode {
    Codec<String> STYLE_CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(e -> e.map(Function.identity(), styles -> String.join(" ", styles)), Either::left);
    MapCodec<CardNode> CODEC = SystemRegistries.CARD_NODE_TYPE.byNameCodec().dispatchMap("type", CardNode::codec, Function.identity());

    MapCodec<? extends CardNode> codec();

    String css();

    @Nullable
    Element render(CardRenderer.Render view);

}
