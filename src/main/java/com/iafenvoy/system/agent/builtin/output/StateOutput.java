package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Built-in result data and its serialization codec.
 */
public record StateOutput(String state) implements CardData {
    public static final MapCodec<StateOutput> CODEC = Codec.STRING.fieldOf("state").xmap(StateOutput::new, StateOutput::state);
    public static final Identifier TEMPLATE = Identifier.parse("system:delivery_state");

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary("system.state." + this.state.toLowerCase(Locale.ROOT));
        builder.put("state", this.state());
        return builder;
    }

    @Override
    public MapCodec<StateOutput> codec() {
        return CODEC;
    }

}
