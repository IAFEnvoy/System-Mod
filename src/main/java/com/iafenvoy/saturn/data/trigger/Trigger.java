package com.iafenvoy.saturn.data.trigger;

import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Data-driven matcher for a runtime {@link TriggerSignal}.
 */
public interface Trigger {
    Codec<Trigger> CODEC = SaturnRegistries.TRIGGER_TYPE.byNameCodec().dispatch("type", Trigger::codec, Function.identity());

    MapCodec<? extends Trigger> codec();

    default boolean matches(TriggerSignal signal) {
        return true;
    }

    ResourceLocation signalType();
}
