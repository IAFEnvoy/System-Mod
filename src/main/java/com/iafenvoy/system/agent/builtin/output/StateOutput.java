package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;

/**
 * Built-in result data and its serialization codec.
 */
public record StateOutput(String state) {
    public static final Codec<StateOutput> CODEC = Codec.STRING.fieldOf("state").xmap(StateOutput::new, StateOutput::state).codec();
}
