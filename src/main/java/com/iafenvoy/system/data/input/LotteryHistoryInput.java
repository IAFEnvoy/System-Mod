package com.iafenvoy.system.data.input;

import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record LotteryHistoryInput(Optional<Identifier> pool, int offset,
                                  Optional<String> order) implements AgentAction {
    public static final SchemaMapCodec<LotteryHistoryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("pool", SchemaCodecs.IDENTIFIER).forGetter(LotteryHistoryInput::pool),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(LotteryHistoryInput::offset),
            f.optional("order", SchemaCodecs.STRING).forGetter(LotteryHistoryInput::order)
    ).apply(i, LotteryHistoryInput::new));

    public LotteryHistoryInput(Optional<Identifier> pool, int offset) {
        this(pool, offset, Optional.empty());
    }

    @Override
    public MapCodec<LotteryHistoryInput> codec() {
        return CODEC;
    }
}
