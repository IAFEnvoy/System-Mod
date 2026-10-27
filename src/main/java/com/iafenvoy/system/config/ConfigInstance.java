package com.iafenvoy.system.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.iafenvoy.system.util.AbstractChangeTrackable;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Owns a configuration snapshot, file lifecycle and deferred persistence for committed changes.
 */
public final class ConfigInstance<T> extends AbstractChangeTrackable implements Supplier<T> {
    private static final Path CONFIG_FOLDER = Path.of("./config/system");
    private static final List<ConfigInstance<?>> CONFIGS = new CopyOnWriteArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();
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

    public static Path getConfigFolder() {
        return CONFIG_FOLDER;
    }

    public Path getFilePath() {
        return getConfigFolder().resolve(this.fileName);
    }

    public synchronized void load() {
        // Keep committed UI changes if a resource reload arrives before the next saving tick.
        if (this.checkDirty() && !this.save()) return;
        try {
            Path target = this.getFilePath();
            if (Files.notExists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream defaults = ConfigInstance.class.getResourceAsStream("/defaults/" + this.fileName)) {
                    if (defaults == null)
                        throw new IOException("Missing bundled config defaults " + target.getFileName() + ", this should not happened!");
                    FileUtils.write(target.toFile(), new InputStreamReader(defaults).readAllAsString(), StandardCharsets.UTF_8);
                }
            }
            this.value = this.codec.parse(JsonOps.INSTANCE, JsonParser.parseString(FileUtils.readFileToString(target.toFile(), StandardCharsets.UTF_8))).getOrThrow();
        } catch (Exception e) {
            LOGGER.error("Failed to load config file: {}", this.fileName, e);
            this.value = this.defaultValue;
        }
    }

    public synchronized boolean save() {
        this.checkDirty();
        try {
            FileUtils.write(this.getFilePath().toFile(), GSON.toJson(this.codec.encodeStart(JsonOps.INSTANCE, this.value).getOrThrow()), StandardCharsets.UTF_8);
            return true;
        } catch (IOException | RuntimeException e) {
            this.markDirty();
            LOGGER.error("Failed to save config file: {}", this.fileName, e);
            return false;
        }
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
