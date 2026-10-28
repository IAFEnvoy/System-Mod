package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.condition.HasModuleCondition;
import com.iafenvoy.system.data.condition.HasSystemCondition;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemEntityConditions {
    public static final DeferredRegister<MapCodec<? extends EntityCondition>> REGISTRY = DeferredRegister.create(SaturnResourceKeys.ENTITY_CONDITION_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasModuleCondition>> HAS_MODULE = REGISTRY.register("has_module", () -> HasModuleCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasSystemCondition>> HAS_SYSTEM = REGISTRY.register("has_system", () -> HasSystemCondition.CODEC);
}
