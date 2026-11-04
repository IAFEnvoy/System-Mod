package com.iafenvoy.system.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One codec boundary for every file; callers choose readable JSON for configuration or NBT for bulk data.
 */
public abstract class FileAccess {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final FileAccess JSON = new JsonFile();
    public static final FileAccess NBT = new NbtFile();

    public <T> T loadOrDefault(Codec<T> codec, Path path, T defaultValue, String jarFilePath) {
        return this.loadOrDefault(codec, path, (Supplier<T>) () -> {
            InputStream jarStream = FileAccess.class.getResourceAsStream(jarFilePath);
            if (jarStream != null) return this.deserialize(codec, new DataInputStream(jarStream)).orElse(defaultValue);
            return defaultValue;
        });
    }

    public <T> T loadOrDefault(Codec<T> codec, Path path, T defaultValue) {
        return this.loadOrDefault(codec, path, (Supplier<T>) () -> defaultValue);
    }

    public <T> T loadOrDefault(Codec<T> codec, Path path, Supplier<T> defaultValue) {
        Optional<T> local = this.load(codec, path);
        if (local.isPresent()) return local.get();
        T value = defaultValue.get();
        this.save(codec, path, value);
        return value;
    }

    public <T> Optional<T> load(Codec<T> codec, Path path) {
        try {
            return this.decode(codec, Files.readAllBytes(path));
        } catch (Exception e) {
            LOGGER.error("Failed to load file: {}", path, e);
            return Optional.empty();
        }
    }

    public <T> boolean save(Codec<T> codec, Path path, T value) {
        Optional<byte[]> encoded = this.encode(codec, value);
        if (encoded.isEmpty()) return false;
        try {
            Files.write(path, encoded.get());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save file: {}", path, e);
            return false;
        }
    }

    /**
     * Encode into memory so a caller can measure, hash or place the exact bytes before touching the disk.
     */
    public final <T> Optional<byte[]> encode(Codec<T> codec, T value) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        return this.serialize(codec, value, buffer) ? Optional.of(buffer.toByteArray()) : Optional.empty();
    }

    public final <T> Optional<T> decode(Codec<T> codec, byte[] data) {
        return this.deserialize(codec, new ByteArrayInputStream(data));
    }

    protected abstract <T> Optional<T> deserialize(Codec<T> codec, InputStream stream);

    protected abstract <T> boolean serialize(Codec<T> codec, T data, OutputStream stream);

    private static class JsonFile extends FileAccess {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

        @Override
        protected <T> Optional<T> deserialize(Codec<T> codec, InputStream stream) {
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).resultOrPartial(e -> LOGGER.error("Failed to deserialize JSON: {}", e));
            } catch (Exception e) {
                LOGGER.error("Failed to read JSON from stream", e);
                return Optional.empty();
            }
        }

        @Override
        protected <T> boolean serialize(Codec<T> codec, T data, OutputStream stream) {
            Optional<JsonElement> encoded = codec.encodeStart(JsonOps.INSTANCE, data)
                    .resultOrPartial(e -> LOGGER.error("Failed to serialize JSON: {}", e));
            if (encoded.isEmpty()) return false;
            try (Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                GSON.toJson(encoded.get(), writer);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to write JSON to stream", e);
                return false;
            }
        }
    }

    private static class NbtFile extends FileAccess {
        @Override
        protected <T> Optional<T> deserialize(Codec<T> codec, InputStream stream) {
            try (DataInputStream input = new DataInputStream(stream)) {
                return codec.parse(NbtOps.INSTANCE, NbtIo.readAnyTag(input, NbtAccounter.unlimitedHeap())).resultOrPartial(e -> LOGGER.error("Failed to deserialize NBT: {}", e));
            } catch (Exception e) {
                LOGGER.error("Failed to read NBT from stream", e);
                return Optional.empty();
            }
        }

        @Override
        protected <T> boolean serialize(Codec<T> codec, T data, OutputStream stream) {
            try (DataOutputStream output = new DataOutputStream(stream)) {
                NbtIo.writeAnyTag(codec.encodeStart(NbtOps.INSTANCE, data).resultOrPartial(e -> LOGGER.error("Failed to serialize NBT: {}", e)).orElseThrow(), output);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to write NBT to stream", e);
                return false;
            }
        }
    }
}
