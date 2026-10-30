package com.iafenvoy.system.agent.database;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Typed document collections share an atomic file boundary. Call from workers: reads may lazily load disk.
 * Encoded values stay private, so callers cannot mutate stored data without a successful write.
 */
public final class AgentDatabase implements AutoCloseable {
    private static final Codec<Map<Identifier, Map<String, Dynamic<?>>>> CODEC = Codec.unboundedMap(
            Identifier.CODEC, Codec.unboundedMap(Codec.STRING, Codec.PASSTHROUGH));
    private final Path file;
    private final Map<EntryKey, KnowledgeIndex> indexes = new HashMap<>();
    private Map<Identifier, Map<String, Dynamic<?>>> tables;
    private boolean closed;

    AgentDatabase(Path file) {
        this.file = file;
    }

    private record EntryKey(Identifier table, String key) {
    }

    public synchronized <T> Optional<T> get(DatabaseTable<T> table, String key) {
        Dynamic<?> value = this.entries(table.id()).get(key);
        return value == null ? Optional.empty() : Optional.of(table.codec().parse(value).getOrThrow());
    }

    public synchronized <T> Map<String, T> entries(DatabaseTable<T> table) {
        Map<String, T> values = new LinkedHashMap<>();
        this.entries(table.id()).forEach((key, value) -> values.put(key, table.codec().parse(value).getOrThrow()));
        return Map.copyOf(values);
    }

    public synchronized <T> void put(DatabaseTable<T> table, String key, T value) {
        Map<String, Dynamic<?>> entries = new LinkedHashMap<>(this.entries(table.id()));
        Dynamic<?> encoded = encode(table, value);
        if (encoded.equals(entries.put(key, encoded))) return;
        this.commit(table.id(), entries);
        this.indexes.remove(new EntryKey(table.id(), key));
    }

    public synchronized void remove(DatabaseTable<?> table, String key) {
        Map<String, Dynamic<?>> entries = new LinkedHashMap<>(this.entries(table.id()));
        if (entries.remove(key) == null) return;
        this.commit(table.id(), entries);
        this.indexes.remove(new EntryKey(table.id(), key));
    }

    /**
     * Replace one collection atomically; unrelated module collections and scopes remain intact.
     */
    public synchronized <T> void replace(DatabaseTable<T> table, Map<String, T> values) {
        this.load();
        Map<String, Dynamic<?>> entries = new LinkedHashMap<>();
        values.forEach((key, value) -> entries.put(key, encode(table, value)));
        this.commit(table.id(), entries);
        this.indexes.keySet().removeIf(key -> key.table().equals(table.id()));
    }

    public static DatabaseTable<List<KnowledgeIndex.Document>> documents(Identifier source) {
        return new DatabaseTable<>(source, KnowledgeIndex.Document.CODEC.listOf());
    }

    /**
     * Index snapshots are immutable; ranking does not hold the file/collection monitor.
     */
    public List<KnowledgeIndex.Hit> search(Identifier source, String language, Set<String> allowed, String query, int limit) {
        KnowledgeIndex index = this.index(source, language);
        return index == null ? List.of() : index.search(allowed, query, language, limit);
    }

    public void prepareIndex(Identifier source, String language) {
        this.index(source, language);
    }

    private KnowledgeIndex index(Identifier source, String language) {
        EntryKey key = new EntryKey(source, language);
        KnowledgeIndex index;
        Dynamic<?> value;
        synchronized (this) {
            value = this.entries(source).get(language);
            if (value == null) return null;
            index = this.indexes.get(key);
        }
        if (index == null) {
            index = new KnowledgeIndex(documents(source).codec().parse(value).getOrThrow(), language);
            synchronized (this) {
                // A concurrent replacement must not be overwritten by an index built from an older snapshot.
                if (!this.closed && this.entries(source).get(language) == value) this.indexes.put(key, index);
            }
        }
        return index;
    }

    private static <T> Dynamic<?> encode(DatabaseTable<T> table, T value) {
        return new Dynamic<>(JsonOps.INSTANCE, table.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow());
    }

    private Map<String, Dynamic<?>> entries(Identifier table) {
        this.load();
        return this.tables.getOrDefault(table, Map.of());
    }

    private void load() {
        if (this.closed) throw new AgentException("STORAGE_UNAVAILABLE");
        if (this.tables != null) return;
        try {
            this.tables = Files.exists(this.file) ? CodecIO.parse(CODEC, Files.readString(this.file, StandardCharsets.UTF_8)) : Map.of();
        } catch (IOException | RuntimeException error) {
            // Preserve unreadable files instead of replacing potentially unrelated player/module data.
            throw new AgentException("STORAGE_UNAVAILABLE", new IOException("Unable to read agent database: " + this.file, error));
        }
    }

    private void commit(Identifier table, Map<String, Dynamic<?>> entries) {
        Map<Identifier, Map<String, Dynamic<?>>> next = new LinkedHashMap<>(this.tables);
        if (entries.isEmpty()) next.remove(table);
        else next.put(table, Map.copyOf(entries));
        Path temporary = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        try {
            Files.createDirectories(this.file.getParent());
            Files.writeString(temporary, CodecIO.write(CODEC, next), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
            this.tables = Map.copyOf(next);
        } catch (IOException | RuntimeException error) {
            throw new AgentException("STORAGE_UNAVAILABLE", new IOException("Unable to save agent database: " + this.file, error));
        }
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.indexes.clear();
        this.tables = null;
    }

    synchronized void delete() {
        this.close();
        try {
            Files.deleteIfExists(this.file);
            Files.deleteIfExists(this.file.resolveSibling(this.file.getFileName() + ".tmp"));
        } catch (IOException error) {
            throw new AgentException("STORAGE_UNAVAILABLE", new IOException("Unable to delete agent database: " + this.file, error));
        }
    }
}
