package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.action.AddModuleAction;
import com.iafenvoy.system.data.action.RemoveModuleAction;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemEntityActions {
    public static final DeferredRegister<MapCodec<? extends EntityAction>> REGISTRY = DeferredRegister.create(SaturnResourceKeys.ENTITY_ACTION_TYPE, SystemMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<AddModuleAction>> ADD_MODULE = REGISTRY.register("add_module", () -> AddModuleAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityAction>, MapCodec<RemoveModuleAction>> REMOVE_MODULE = REGISTRY.register("remove_module", () -> RemoveModuleAction.CODEC);
}
