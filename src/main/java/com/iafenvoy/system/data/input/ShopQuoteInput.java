package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record ShopQuoteInput(Identifier offerId, Optional<String> catalogRevision, Optional<ShopTrade> trade,
                             int count) implements AgentAction {
    public static final SchemaMapCodec<ShopQuoteInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("offerId", SchemaCodecs.IDENTIFIER).forGetter(ShopQuoteInput::offerId),
            f.optional("catalogRevision", SchemaCodecs.STRING).forGetter(ShopQuoteInput::catalogRevision),
            f.optional("trade", ShopTrade.SCHEMA).forGetter(ShopQuoteInput::trade),
            f.optional("count", SchemaCodecs.NON_NEGATIVE_INT, 1).forGetter(ShopQuoteInput::count)
    ).apply(i, ShopQuoteInput::new));

    public ShopQuoteInput(Identifier offerId, Optional<String> catalogRevision) {
        this(offerId, catalogRevision, Optional.empty(), 1);
    }

    public ShopQuoteInput(Identifier offerId, Optional<String> catalogRevision, Optional<ShopTrade> trade) {
        this(offerId, catalogRevision, trade, 1);
    }

    @Override
    public MapCodec<ShopQuoteInput> codec() {
        return CODEC;
    }
}
