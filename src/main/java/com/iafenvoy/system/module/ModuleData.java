package com.iafenvoy.system.module;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.data.SystemModule;
import net.minecraft.core.Registry;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Storage access belongs inside an AgentStore transaction, never in PlayerData business helpers.
 */
public final class ModuleData {
    private ModuleData() {
    }

    public static <T extends DataStorage> T get(PlayerData data, ModuleStorage<T> storage) {
        return get(data, storage, 0L);
    }

    private static <T extends DataStorage> T get(PlayerData data, ModuleStorage<T> storage, long gameTime) {
        return data.moduleData.get(storage.owner(), storage.type()).orElseGet(() -> {
            T value = Objects.requireNonNull(storage.factory().get());
            if (value.getClass() != storage.type() || value.codec() != storage.codec())
                throw new IllegalArgumentException("Incorrect module storage factory " + storage.id());
            data.moduleData.set(storage.owner(), value, gameTime);
            return value;
        });
    }

    public static void initialize(PlayerData data, ModuleStorage<?> storage, long gameTime) {
        get(data, storage, gameTime);
    }

    /**
     * Read existing values across declaring modules without creating disabled modules' data.
     */
    public static <T extends DataStorage> Stream<T> values(PlayerData data, Class<T> type, Registry<SystemModule> modules) {
        return modules.stream().filter(SystemModule::available).flatMap(module -> module.server().stream())
                .flatMap(module -> module.storages().stream()).distinct()
                .filter(storage -> storage.type() == type)
                .flatMap(storage -> data.moduleData.get(storage.owner(), type).stream());
    }
}
