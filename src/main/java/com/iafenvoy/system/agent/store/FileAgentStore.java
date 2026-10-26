package com.iafenvoy.system.agent.store;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.util.CodecIO;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * One world, one writer process; transactions atomically replace one player's file.
 */
public final class FileAgentStore implements AgentStore {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final Path root;
    private final Object[] locks = new Object[64];
    private final FileChannel channel;
    private final FileLock owner;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private boolean closed;

    public FileAgentStore(Path root) throws IOException {
        this.root = root;
        Arrays.setAll(this.locks, ignored -> new Object());
        Files.createDirectories(root);
        this.channel = FileChannel.open(root.resolve("store.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = this.channel.tryLock();
        } catch (RuntimeException | IOException e) {
            this.channel.close();
            throw e;
        }
        if (acquired == null) {
            this.channel.close();
            throw new IOException("Agent store already open");
        }
        this.owner = acquired;
    }

    private PlayerData load(UUID player) throws IOException {
        Path path = this.root.resolve(player + ".json");
        if (!Files.exists(path)) return new PlayerData();
        if (Files.size(path) > MAX_BYTES) throw new AgentException("STORAGE_FULL");
        PlayerData data = CodecIO.read(PlayerData.CODEC, path, MAX_BYTES);
        if (data == null || data.version != 1 || data.history == null || data.tasks == null || data.inbox == null
                || data.requests == null || data.requestInputs == null || data.signIns == null || data.operations == null
                || data.baselines == null || data.unlocked == null || data.usageDate == null || data.purchases == null)
            throw new IOException("Invalid agent store version or structure");
        prune(data);
        return data;
    }

    @Override
    public <T> T read(UUID player, Function<PlayerData, T> query) {
        return this.whileOpen(() -> this.readPlayer(player, query));
    }

    private <T> T readPlayer(UUID player, Function<PlayerData, T> query) {
        synchronized (this.locks[(player.hashCode() & Integer.MAX_VALUE) % this.locks.length]) {
            try {
                return query.apply(this.load(player));
            } catch (IOException e) {
                throw new AgentException("STORAGE_UNAVAILABLE");
            }
        }
    }

    @Override
    public <T> T update(UUID player, Function<PlayerData, T> transaction) {
        return this.whileOpen(() -> this.updatePlayer(player, transaction));
    }

    private <T> T updatePlayer(UUID player, Function<PlayerData, T> transaction) {
        // Hold the player's stripe across load, transaction and durable replacement to avoid lost updates.
        synchronized (this.locks[(player.hashCode() & Integer.MAX_VALUE) % this.locks.length]) {
            Path temp = this.root.resolve(player + ".tmp");
            try {
                PlayerData data = this.load(player);
                T result = transaction.apply(data);
                prune(data);
                byte[] bytes = CodecIO.write(PlayerData.CODEC, data).getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_BYTES) throw new AgentException("STORAGE_FULL");
                try (FileChannel file = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) file.write(buffer);
                    file.force(true);
                }
                // Do not silently downgrade to a non-atomic replacement on unsupported filesystems.
                Files.move(temp, this.root.resolve(player + ".json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return result;
            } catch (IOException e) {
                throw new AgentException("STORAGE_UNAVAILABLE");
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void prune(PlayerData data) {
        // Chat retention prunes replay caches only; reward, order and event records keep their own lifetimes.
        long cutoff = System.currentTimeMillis() - 30L * 86400000L;
        data.history.removeIf(message -> message.time() < cutoff);
        while (data.history.size() > 256) data.history.removeFirst();
        Set<String> retainedRequests = new HashSet<>();
        data.history.forEach(message -> retainedRequests.add(message.requestId()));
        data.requests.keySet().removeIf(key -> !retainedRequests.contains(key));
        data.requestInputs.keySet().removeIf(key -> !retainedRequests.contains(key));
        while (data.requests.size() > 128) {
            String key = data.requests.keySet().iterator().next();
            data.requests.remove(key);
            data.requestInputs.remove(key);
        }
    }

    @Override
    public void delete(UUID player) {
        this.whileOpen(() -> {
            synchronized (this.locks[(player.hashCode() & Integer.MAX_VALUE) % this.locks.length]) {
                try {
                    Files.deleteIfExists(this.root.resolve(player + ".tmp"));
                    Files.deleteIfExists(this.root.resolve(player + ".json"));
                } catch (IOException e) {
                    throw new AgentException("STORAGE_UNAVAILABLE");
                }
            }
            return null;
        });
    }

    @Override
    public List<UUID> players() {
        return this.whileOpen(this::listPlayers);
    }

    private List<UUID> listPlayers() {
        try (Stream<Path> files = Files.list(this.root)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .map(name -> UUID.fromString(name.substring(0, name.length() - 5))).toList();
        } catch (IOException | IllegalArgumentException e) {
            throw new AgentException("STORAGE_UNAVAILABLE");
        }
    }

    private <T> T whileOpen(Supplier<T> work) {
        this.lifecycle.readLock().lock();
        try {
            if (this.closed) throw new AgentException("STORAGE_UNAVAILABLE");
            return work.get();
        } finally {
            this.lifecycle.readLock().unlock();
        }
    }

    @Override
    public void close() {
        this.lifecycle.writeLock().lock();
        try {
            if (this.closed) return;
            this.closed = true;
            try {
                this.owner.release();
            } catch (IOException ignored) {
            }
            try {
                this.channel.close();
            } catch (IOException ignored) {
            }
        } finally {
            this.lifecycle.writeLock().unlock();
        }
    }
}
