package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.cost.CurrencyCost;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemCosts {
    public static final DeferredRegister<MapCodec<? extends Cost>> REGISTRY = DeferredRegister.create(SaturnResourceKeys.COST_TYPE, SystemMod.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends Cost>, MapCodec<CurrencyCost>> CURRENCY = REGISTRY.register("currency", () -> CurrencyCost.CODEC);
}
