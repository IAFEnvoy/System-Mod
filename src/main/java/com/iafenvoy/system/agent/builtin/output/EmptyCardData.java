package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardData;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

/** Action-only cards still have a concrete data type, separate from empty module input. */
public enum EmptyCardData implements CardData {
    INSTANCE;
    public static final MapCodec<EmptyCardData> CODEC = MapCodec.unit(INSTANCE);
    public static final Identifier TEMPLATE = Identifier.parse("system:empty");

    @Override
    public MapCodec<EmptyCardData> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
