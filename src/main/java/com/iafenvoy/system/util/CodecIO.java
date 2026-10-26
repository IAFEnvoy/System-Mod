package com.iafenvoy.system.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** The JSON/file boundary is centralized; callers provide a Codec and exchange Java objects. */
public final class CodecIO {
    public static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.xmap(value -> value.convert(JsonOps.INSTANCE).getValue(), value -> new Dynamic<>(JsonOps.INSTANCE, value));

    private CodecIO() {
    }

    public static <A> A parse(Codec<A> codec, String text, int limit) {
        return document(text, limit).read(codec);
    }

    public static Document document(String text, int limit) {
        return new Document(Json.object(text, limit));
    }

    /** Decode independent response sections without reparsing or exposing JSON to the caller. */
    public static final class Document {
        private final JsonElement value;
        private Document(JsonElement value) { this.value = value; }
        public <A> A read(Codec<A> codec) { return AgentCodecs.decode(codec, this.value); }
    }

    public static <A> String write(Codec<A> codec, A value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().toString();
    }

    public static <A> A read(Codec<A> codec, Path path, int limit) throws IOException {
        if (Files.size(path) > limit) throw new IOException("Configuration exceeds byte limit");
        try {
            return parse(codec, Files.readString(path), limit);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid structured file: " + path.getFileName(), invalid);
        }
    }

    public static <A> void save(Codec<A> codec, Path path, A value) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.createDirectories(path.getParent());
        try {
            String text = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    .toJson(codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow());
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
