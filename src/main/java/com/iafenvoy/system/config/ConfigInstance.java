package com.iafenvoy.system.config;

import com.iafenvoy.saturn.util.AbstractChangeTrackable;
import com.iafenvoy.system.util.FileAccess;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Owns a configuration snapshot, file lifecycle and deferred persistence for committed changes.
 */
public final class ConfigInstance<T> extends AbstractChangeTrackable implements Supplier<T> {
    public static final Path CONFIG_FOLDER = Path.of("./config/system");
    private static final List<ConfigInstance<?>> CONFIGS = new CopyOnWriteArrayList<>();
    private final String fileName;
    private final Codec<T> codec;
    private final T defaultValue;
    private volatile T value;

    private ConfigInstance(String fileName, @NotNull Codec<T> codec, @NotNull T defaultValue) {
        this.fileName = fileName;
        this.codec = codec;
        this.defaultValue = this.value = defaultValue;
        // Lazily registered configuration classes must also work after the initial resource reload.
        this.load();
        CONFIGS.add(this);
    }

    public static <T> ConfigInstance<T> of(String fileName, Codec<T> codec, T defaultValue) {
        return new ConfigInstance<>(fileName, codec, defaultValue);
    }

    public static void reloadAll() {
        CONFIGS.forEach(ConfigInstance::load);
    }

    public static void saveAll() {
        CONFIGS.forEach(ConfigInstance::saveIfDirty);
    }

    private synchronized void saveIfDirty() {
        // Consume the flag under the same lock as saving so a reload cannot discard pending changes.
        if (this.checkDirty()) this.save();
    }

    public Path getFilePath() {
        return CONFIG_FOLDER.resolve(this.fileName);
    }

    public synchronized void load() {
        // Keep committed UI changes if a resource reload arrives before the next saving tick.
        if (this.checkDirty() && !this.save()) return;
        this.value = FileAccess.JSON.loadOrDefault(this.codec, this.getFilePath(), this.defaultValue, "/defaults/" + this.fileName);
    }

    public synchronized boolean save() {
        this.checkDirty();
        if (FileAccess.JSON.save(this.codec, this.getFilePath(), this.value)) return true;
        // Keep the pending flag so the next save tick retries a failed write.
        this.markDirty();
        return false;
    }

    /**
     * Replace only committed values; every actual change enters the shared dirty-save queue.
     */
    public synchronized void set(T value) {
        Objects.requireNonNull(value);
        if (Objects.equals(this.value, value)) return;
        this.value = value;
        this.markDirty();
    }

    @Override
    public T get() {
        return this.value;
    }
}
