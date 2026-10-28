package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

/**
 * Built-in result data and its serialization codec.
 */
public record StateOutput(String state) implements CardData {
    public static final MapCodec<StateOutput> CODEC = Codec.STRING.fieldOf("state").xmap(StateOutput::new, StateOutput::state);
    public static final Identifier TEMPLATE = Identifier.parse("system:delivery_state");

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        builder.put("state", this.state());
    }

    @Override
    public MapCodec<StateOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
