package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.data.storage.builtin.*;
import com.iafenvoy.saturn.data.storage.runtime.*;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code data_storage_type} entries.
 */
public final class SaturnDataStorages {
    public static final DeferredRegister<MapCodec<? extends DataStorage>> REGISTRY = DeferredRegister.create(SaturnRegistries.DATA_STORAGE_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("empty", () -> EmptyDataStorage.CODEC);
        REGISTRY.register("cooldown", () -> CooldownDataStorage.CODEC);
        REGISTRY.register("charges", () -> ChargesDataStorage.CODEC);
        REGISTRY.register("toggle", () -> ToggleDataStorage.CODEC);
        REGISTRY.register("timer", () -> TimerDataStorage.CODEC);
        REGISTRY.register("target_lock", () -> TargetLockDataStorage.CODEC);
        REGISTRY.register("container", () -> ContainerDataStorage.CODEC);
        REGISTRY.register("cast_deadline", () -> CastDeadline.CODEC);
        REGISTRY.register("channel_pulse", () -> ChannelPulse.CODEC);
        REGISTRY.register("active_state", () -> ActiveState.CODEC);
        REGISTRY.register("entry_began", () -> EntryBegan.CODEC);
        REGISTRY.register("idle_countdown", () -> IdleCountdown.CODEC);
        REGISTRY.register("wait_countdown", () -> WaitCountdown.CODEC);
    }

    private SaturnDataStorages() {
    }
}
