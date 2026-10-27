package com.iafenvoy.system.speech;

import com.iafenvoy.system.SystemMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber
public final class SystemSounds {
    public static final SoundEvent NOTIFICATION = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "notification"));

    private SystemSounds() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, NOTIFICATION.location(), () -> NOTIFICATION);
    }
}
