package com.iafenvoy.system.agent;

import com.iafenvoy.system.registry.SystemRegistries;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * An action is its typed input; the registered MapCodec supplies its identity and dispatch format.
 */
public interface AgentAction {
    Codec<AgentAction> CODEC = SystemRegistries.ACTION_TYPE.byNameCodec().dispatch("moduleId", AgentAction::codec, codec -> codec);

    MapCodec<? extends AgentAction> codec();

    default Identifier id() {
        return Objects.requireNonNull(SystemRegistries.ACTION_TYPE.getKey(this.codec()), "Unregistered Agent action codec");
    }

    /**
     * Java parsers obey the same type identity and field constraints as decoded network inputs.
     */
    static <I extends AgentAction> I validate(MapCodec<I> codec, I input) {
        if (input.codec() != codec) throw new IllegalArgumentException("Incorrect Agent action codec");
        CodecIO.write(CODEC, input);
        return input;
    }
}
