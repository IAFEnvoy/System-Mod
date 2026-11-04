package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Built-in result data and its serialization codec.
 */
public record StateOutput(String state) implements CardData {
    public static final MapCodec<StateOutput> CODEC = Codec.STRING.fieldOf("state").xmap(StateOutput::new, StateOutput::state);
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "delivery_state");

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

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("state", "", "", this.state(), List.of()));
    }

}
