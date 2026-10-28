package com.iafenvoy.system.registry;

import com.iafenvoy.system.SystemMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class SystemSoundEvents {
    public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, SystemMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> NOTIFICATION = register("notification");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String id) {
        return REGISTRY.register(id, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, id)));
    }
}
