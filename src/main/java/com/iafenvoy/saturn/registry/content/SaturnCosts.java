package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.builtin.ItemCost;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code cost_type} entries.
 */
public final class SaturnCosts {
    public static final DeferredRegister<MapCodec<? extends Cost>> REGISTRY = DeferredRegister.create(SaturnRegistries.COST_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("item", () -> ItemCost.CODEC);
        // KubeJS is compile-only: without the mod present the bridge classes must not be touched.
    }

    private SaturnCosts() {
    }
}
