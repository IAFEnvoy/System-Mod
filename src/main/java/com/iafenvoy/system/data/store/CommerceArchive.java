package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.FileAccess;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.util.AtomicFiles;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Immutable full receipts live outside the bounded player file. Call only on storage workers.
 */
public final class CommerceArchive {
    private final Path root;

    public CommerceArchive(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private Path path(UUID player, String scope, PurchaseType type, String id) {
        try {
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException();
            if (!UUID.fromString(scope).toString().equals(scope)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            throw new AgentException(AgentFailure.INVALID_INPUT, error);
        }
        return this.root.resolve(player.toString()).resolve(type.getSerializedName()).resolve(scope).resolve(id.substring(0, 2)).resolve(id + ".dat");
    }

    public Optional<Purchase> get(AgentStore store, UUID player, String scope, PurchaseType type, String id) {
        Path file = this.path(player, scope, type, id);
        try {
            if (Files.notExists(file)) return Optional.empty();
            Purchase purchase = FileAccess.NBT_COMPRESSED.load(store.codec(Purchase.CODEC), file)
                    .orElseThrow(() -> new IOException("Unreadable commerce archive " + file));
            if (!id.equals(purchase.id) || purchase.order.type() != type)
                throw new IOException("Mismatched commerce archive " + file);
            return Optional.of(purchase);
        } catch (IOException | RuntimeException error) {
            throw failure("read", file, error);
        }
    }

    public void put(AgentStore store, UUID player, String scope, Purchase purchase) {
        Path file = this.path(player, scope, purchase.order.type(), purchase.id);
        try {
            Optional<Purchase> existing = this.get(store, player, scope, purchase.order.type(), purchase.id);
            if (existing.isPresent()) {
                Purchase prior = existing.get();
                Codec<Purchase> codec = store.codec(Purchase.CODEC);
                if (!codec.encodeStart(NbtOps.INSTANCE, prior).getOrThrow().equals(codec.encodeStart(NbtOps.INSTANCE, purchase).getOrThrow()))
                    throw new IOException("Conflicting commerce archive " + file);
                this.index(player, scope, purchase);
                return;
            }
            byte[] bytes = FileAccess.NBT_COMPRESSED.encode(store.codec(Purchase.CODEC), purchase)
                    .orElseThrow(() -> new IOException("Unable to encode commerce archive " + file));
            AtomicFiles.replace(file, bytes);
            this.index(player, scope, purchase);
        } catch (IOException | RuntimeException error) {
            throw failure("write", file, error);
        }
    }

    private Path history(UUID player, String scope, Optional<Identifier> pool) {
        Path root = this.path(player, scope, PurchaseType.LOTTERY, "00000000-0000-0000-0000-000000000000").getParent().getParent().resolve("history");
        return pool.map(id -> root.resolve(UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8)).toString())).orElse(root);
    }

    /**
     * A small time-ordered index avoids decoding every archived receipt just to show one page.
     */
    private void index(UUID player, String scope, Purchase purchase) throws IOException {
        if (purchase.order.type() != PurchaseType.LOTTERY) return;
        Path file = this.history(player, scope, Optional.of(purchase.order.source()))
                .resolve(String.format(Locale.ROOT, "%019d-%s.dat", purchase.created, purchase.id));
        if (Files.exists(file)) return;
        byte[] bytes = FileAccess.NBT_COMPRESSED.encode(Codec.STRING.fieldOf("order").codec(), purchase.id)
                .orElseThrow(() -> new IOException("Unable to encode commerce history index " + file));
        AtomicFiles.replace(file, bytes);
    }

    public record Page(List<Purchase> orders, int offset, int total) {
    }

    /**
     * Count archived results once; exclude live IDs because archiving can leave the same receipt in both places.
     */
    public Map<Identifier, Map<Identifier, Integer>> rewardCounts(AgentStore store, UUID player, String scope, Set<String> live) {
        Path directory = this.history(player, scope, Optional.empty()).getParent();
        Map<Identifier, Map<Identifier, Integer>> wins = new HashMap<>();
        try {
            if (Files.notExists(directory)) return wins;
            try (Stream<@NotNull Path> files = Files.walk(directory, 2)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(file -> file.getParent().getFileName().toString().matches("[0-9a-f]{2}"))
                        .filter(file -> file.getFileName().toString().matches("[0-9a-f-]{36}\\.dat")).toList()) {
                    String id = file.getFileName().toString().substring(0, 36);
                    if (live.contains(id)) continue;
                    Purchase purchase = this.get(store, player, scope, PurchaseType.LOTTERY, id).orElseThrow();
                    if ("COMPLETED".equals(purchase.state))
                        purchase.order.rewardCounts().forEach((reward, count) -> wins.computeIfAbsent(purchase.order.source(), pool -> new HashMap<>())
                                .merge(reward, count, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b)));
                }
            }
            return wins;
        } catch (IOException | RuntimeException error) {
            throw failure("restore reward counts from", directory, error);
        }
    }

    /**
     * Index archives written before history browsing existed once, on the storage worker.
     */
    private void ensureHistory(AgentStore store, UUID player, String scope) throws IOException {
        Path history = this.history(player, scope, Optional.empty());
        Path ready = history.resolve("ready.dat");
        if (Files.exists(ready)) return;
        Path orders = history.getParent();
        if (Files.exists(orders)) try (Stream<@NotNull Path> files = Files.walk(orders, 2)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(file -> file.getParent().getFileName().toString().matches("[0-9a-f]{2}"))
                    .filter(file -> file.getFileName().toString().matches("[0-9a-f-]{36}\\.dat")).toList()) {
                String id = file.getFileName().toString().substring(0, 36);
                this.index(player, scope, this.get(store, player, scope, PurchaseType.LOTTERY, id).orElseThrow());
            }
        }
        AtomicFiles.replace(ready, FileAccess.NBT_COMPRESSED.encode(Codec.BOOL.fieldOf("complete").codec(), true)
                .orElseThrow(() -> new IOException("Unable to encode history marker " + ready)));
    }

    public Page history(AgentStore store, UUID player, String scope, Optional<Identifier> pool, List<Purchase> live, int requested, int count) {
        Path directory = this.history(player, scope, pool);
        try {
            this.ensureHistory(store, player, scope);
            Map<String, Purchase> current = new HashMap<>();
            live.forEach(order -> current.put(order.id, order));
            Map<String, Path> indices = new HashMap<>();
            if (Files.exists(directory)) try (Stream<@NotNull Path> files = Files.walk(directory)) {
                files.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().matches("[0-9]{19}-[0-9a-f-]{36}\\.dat"))
                        .filter(file -> !current.containsKey(file.getFileName().toString().substring(20, 56)))
                        .forEach(file -> indices.put(file.getFileName().toString(), file));
            }
            ArrayList<String> keys = new ArrayList<>(indices.keySet());
            live.forEach(order -> keys.add(String.format(Locale.ROOT, "%019d-%s.dat", order.created, order.id)));
            keys.sort(Comparator.reverseOrder());
            int offset = Math.min(Math.max(0, requested) / count, Math.max(0, keys.size() - 1) / count) * count;
            List<Purchase> orders = new ArrayList<>();
            for (String key : keys.stream().skip(offset).limit(count).toList()) {
                Purchase present = current.get(key.substring(20, 56));
                if (present != null) {
                    orders.add(present);
                    continue;
                }
                Path file = indices.get(key);
                String id = FileAccess.NBT_COMPRESSED.load(Codec.STRING.fieldOf("order").codec(), file)
                        .orElseThrow(() -> new IOException("Unreadable commerce history index " + file));
                if (!id.equals(key.substring(20, 56)))
                    throw new IOException("Mismatched commerce history reference " + file);
                Purchase purchase = this.get(store, player, scope, PurchaseType.LOTTERY, id)
                        .orElseThrow(() -> new IOException("Missing indexed commerce order " + id));
                if (pool.filter(value -> !value.equals(purchase.order.source())).isPresent())
                    throw new IOException("Mismatched commerce history index " + file);
                orders.add(purchase);
            }
            return new Page(List.copyOf(orders), offset, keys.size());
        } catch (IOException | RuntimeException error) {
            throw failure("list", directory, error);
        }
    }

    public void delete(UUID player) {
        Path directory = this.root.resolve(player.toString());
        if (Files.notExists(directory)) return;
        try (Stream<@NotNull Path> files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        } catch (IOException error) {
            throw failure("delete", directory, error);
        }
    }

    private static AgentException failure(String operation, Path path, Exception error) {
        SystemMod.LOGGER.error("Unable to {} commerce archive {}", operation, path, error);
        return new AgentException(AgentFailure.STORAGE_UNAVAILABLE, error);
    }
}
