package com.iafenvoy.saturn.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import org.slf4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One codec boundary for every file; callers choose readable JSON for configuration or NBT for bulk data.
 */
public abstract class FileAccess {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final FileAccess JSON = new JsonFile();
    public static final FileAccess NBT = nbt(false);
    public static final FileAccess NBT_COMPRESSED = nbt(true);

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
            write(path, encoded.get());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save file: {}", path, e);
            return false;
        }
    }

    /**
     * Atomically replace a file with already encoded bytes, without encoding or compressing them again.
     * Business-level read/modify/write transactions still belong to the caller.
     */
    public static void write(Path path, byte[] bytes) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        // Separate temporary files keep independent writers from truncating each other's pending output.
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) file.write(buffer);
                file.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException error) {
                LOGGER.warn("Failed to remove temporary file: {}", temporary, error);
            }
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

    private static FileAccess nbt(boolean compressed) {
        // The anonymous adapter has no separate initialization entry point that can lock against FileAccess.
        return new FileAccess() {
            @Override
            protected <T> Optional<T> deserialize(Codec<T> codec, InputStream stream) {
                try (InputStream input = stream) {
                    return codec.parse(NbtOps.INSTANCE, NbtFiles.read(input)).resultOrPartial(e -> LOGGER.error("Failed to deserialize NBT: {}", e));
                } catch (Exception e) {
                    LOGGER.error("Failed to read NBT from stream", e);
                    return Optional.empty();
                }
            }

            @Override
            protected <T> boolean serialize(Codec<T> codec, T data, OutputStream stream) {
                try (OutputStream output = stream) {
                    NbtFiles.write(codec.encodeStart(NbtOps.INSTANCE, data).resultOrPartial(e -> LOGGER.error("Failed to serialize NBT: {}", e)).orElseThrow(), output, compressed);
                    return true;
                } catch (Exception e) {
                    LOGGER.error("Failed to write NBT to stream", e);
                    return false;
                }
            }
        };
    }
}
