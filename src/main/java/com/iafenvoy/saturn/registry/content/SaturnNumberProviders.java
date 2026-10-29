package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code number_provider_type} entries.
 */
public final class SaturnNumberProviders {
    public static final DeferredRegister<MapCodec<? extends NumberProvider>> REGISTRY = DeferredRegister.create(SaturnRegistries.NUMBER_PROVIDER_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("constant", () -> Constant.MAP_CODEC);
        REGISTRY.register("expression", () -> Expression.MAP_CODEC);
        REGISTRY.register("context_variable", () -> ContextVariable.MAP_CODEC);
        REGISTRY.register("sum", () -> Sum.MAP_CODEC);
        REGISTRY.register("uniform", () -> Uniform.MAP_CODEC);
        REGISTRY.register("binomial", () -> Binomial.MAP_CODEC);
        REGISTRY.register("weighted_list", () -> WeightedList.MAP_CODEC);
        REGISTRY.register("conditional", () -> Conditional.MAP_CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
    }

    private SaturnNumberProviders() {
    }
}
