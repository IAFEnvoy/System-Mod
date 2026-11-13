package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.FileAccess;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.chat.ConversationMode;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.config.file.StorageConfig;
import com.iafenvoy.system.util.AtomicFiles;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * One world, one writer process; transactions atomically replace one player's file.
 * Business state and the conversation replay cache live in separate files with separate budgets, so neither can
 * permanently block writes to the other. Codec work goes through {@link FileAccess}; this class owns placement.
 */
public final class FileAgentStore implements AgentStore {
    private static final String SUFFIX = ".dat";
    private final Codec<Conversation> chatCodec;
    private final HolderLookup.Provider registries;
    private final Path playerRoot;
    private final Path chatRoot;
    private final Supplier<ConversationMode> defaultMode;
    private final Codec<PlayerData> businessCodec;
    private final ReentrantLock[] locks = new ReentrantLock[64];
    private final Map<UUID, CachedPlayer> loaded = new ConcurrentHashMap<>();
    private final FileChannel channel;
    private final FileLock owner;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private boolean closed;

    public FileAgentStore(Path root, Supplier<ConversationMode> defaultMode, HolderLookup.Provider registries) throws IOException {
        this.registries = registries;
        this.chatCodec = this.codec(Conversation.CODEC.codec());
        this.playerRoot = root.resolve("players");
        this.chatRoot = root.resolve("chat");
        this.defaultMode = Objects.requireNonNull(defaultMode);
        this.businessCodec = this.codec(PlayerData.codec(defaultMode));
        Arrays.setAll(this.locks, ignored -> new ReentrantLock());
        Files.createDirectories(this.playerRoot);
        Files.createDirectories(this.chatRoot);
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

    private static StorageConfig config() {
        return StorageConfig.INSTANCE.get();
    }

    @Override
    public <T> Codec<T> codec(Codec<T> codec) {
        return CodecIO.contextual(codec, this.registries);
    }

    private CachedPlayer load(UUID player) throws IOException {
        CachedPlayer cached = this.loaded.get(player);
        if (cached != null) return cached;
        PlayerData data = this.loadBusiness(player);
        byte[] chat = this.readChat(player);
        if (chat != null) data = data.withConversation(this.decodeChat(chat));
        this.prune(data.conversation);
        CachedPlayer created = new CachedPlayer(data);
        created.chatDigest = chat == null ? null : digest(chat);
        this.loaded.put(player, created);
        return created;
    }

    private PlayerData loadBusiness(UUID player) throws IOException {
        Path path = this.playerRoot.resolve(player + SUFFIX);
        if (!Files.exists(path)) return new PlayerData(this.defaultMode.get());
        if (Files.size(path) > config().businessBytes()) throw new AgentException(AgentFailure.STORAGE_FULL);
        byte[] bytes = Files.readAllBytes(path);
        PlayerData value = FileAccess.NBT_COMPRESSED.decode(this.businessCodec, bytes).orElseThrow(() -> new IOException("Unable to decode agent store: " + path.getFileName()));
        if (value.version != 1) throw new IOException("Invalid agent store version");
        return value;
    }

    private byte[] readChat(UUID player) throws IOException {
        Path path = this.chatRoot.resolve(player + SUFFIX);
        if (!Files.exists(path)) return null;
        // The replay cache is disposable; an oversized file is replaced instead of blocking the player.
        if (Files.size(path) > config().chatBytes()) return null;
        return Files.readAllBytes(path);
    }

    private Conversation decodeChat(byte[] bytes) {
        return FileAccess.NBT_COMPRESSED.decode(this.chatCodec, bytes).orElseGet(Conversation::new);
    }

    @Override
    public <T> T read(UUID player, Function<PlayerData, T> query) {
        return this.whileOpen(() -> this.readPlayer(player, query));
    }

    @Override
    public <T> Optional<T> readLoaded(UUID player, Function<PlayerData, T> query) {
        if (!this.lifecycle.readLock().tryLock()) return Optional.empty();
        try {
            if (this.closed) return Optional.empty();
            CachedPlayer cached = this.loaded.get(player);
            // Writers replace the published version only after committing; callers always receive a detached copy.
            return cached == null ? Optional.empty() : Optional.ofNullable(query.apply(cached.data.copy()));
        } finally {
            this.lifecycle.readLock().unlock();
        }
    }

    private ReentrantLock lock(UUID player) {
        return this.locks[(player.hashCode() & Integer.MAX_VALUE) % this.locks.length];
    }

    private <T> T readPlayer(UUID player, Function<PlayerData, T> query) {
        ReentrantLock lock = this.lock(player);
        lock.lock();
        try {
            // Returned business records cannot mutate the authoritative cache outside a transaction.
            return query.apply(this.load(player).data.copy());
        } catch (IOException e) {
            throw storageFailure("read", this.playerRoot.resolve(player + SUFFIX), e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T update(UUID player, Function<PlayerData, T> transaction) {
        return this.whileOpen(() -> {
            ReentrantLock lock = this.lock(player);
            lock.lock();
            try {
                CachedPlayer cached = this.load(player);
                PlayerData data = cached.data.copy();
                T result = transaction.apply(data);
                this.saveBusiness(player, data);
                this.saveChat(player, data.conversation, cached);
                cached.data = data;
                cached.dirty = false;
                return result;
            } catch (IOException e) {
                throw storageFailure("save", this.playerRoot.resolve(player + SUFFIX), e);
            } finally {
                lock.unlock();
            }
        });
    }

    @Override
    public boolean updateLoaded(UUID player, Predicate<PlayerData> transaction) {
        // Never let a game tick wait for disk I/O or a worker holding this player's transaction lock.
        if (!this.lifecycle.readLock().tryLock()) return false;
        ReentrantLock lock = this.lock(player);
        boolean acquired = false;
        try {
            if (this.closed || !(acquired = lock.tryLock())) return false;
            CachedPlayer cached = this.loaded.get(player);
            if (cached == null) return false;
            PlayerData next = cached.data.copy();
            if (!transaction.test(next)) return false;
            cached.data = next;
            cached.dirty = true;
            return true;
        } finally {
            if (acquired) lock.unlock();
            this.lifecycle.readLock().unlock();
        }
    }

    @Override
    public void flush(UUID player) {
        this.whileOpen(() -> {
            ReentrantLock lock = this.lock(player);
            lock.lock();
            try {
                CachedPlayer cached = this.loaded.get(player);
                if (cached != null && cached.dirty) {
                    // A module tick cannot touch the conversation, so only the business file is rewritten here.
                    this.saveBusiness(player, cached.data);
                    cached.dirty = false;
                }
            } catch (IOException e) {
                throw storageFailure("flush", this.playerRoot.resolve(player + SUFFIX), e);
            } finally {
                lock.unlock();
            }
            return null;
        });
    }

    private void saveBusiness(UUID player, PlayerData data) throws IOException {
        byte[] bytes = encode(this.businessCodec, data, player);
        // The journal bounds itself by encoded size, so the business file no longer grows without a limit.
        if (bytes.length > config().businessBytes()) throw new AgentException(AgentFailure.STORAGE_FULL);
        AtomicFiles.replace(this.playerRoot.resolve(player + SUFFIX), bytes);
    }

    private void saveChat(UUID player, Conversation conversation, CachedPlayer cached) {
        Path path = this.chatRoot.resolve(player + SUFFIX);
        try {
            this.prune(conversation);
            byte[] bytes = encode(this.chatCodec, conversation, player);
            // Full lists can outweigh count-based retention; evict exchanges, never business records.
            while (bytes.length > config().chatBytes() && !conversation.history.isEmpty()) {
                String request = conversation.history.getFirst().requestId();
                conversation.history.removeIf(message -> message.requestId().equals(request));
                conversation.requests.remove(request);
                conversation.requestInputs.remove(request);
                bytes = encode(this.chatCodec, conversation, player);
            }
            while (bytes.length > config().chatBytes() && !conversation.requests.isEmpty()) {
                String request = conversation.requests.keySet().iterator().next();
                conversation.requests.remove(request);
                conversation.requestInputs.remove(request);
                bytes = encode(this.chatCodec, conversation, player);
            }
            // Nothing to store yet; an empty cache must not create a file for every player.
            if (cached.chatDigest == null && conversation.history.isEmpty()
                    && conversation.requests.isEmpty() && conversation.requestInputs.isEmpty()) return;
            if (bytes.length > config().chatBytes()) {
                SystemMod.LOGGER.warn("Skipping conversation cache for {}: {} bytes exceed {}", player, bytes.length, config().chatBytes());
                return;
            }
            byte[] digest = digest(bytes);
            if (cached.chatDigest != null && Arrays.equals(digest, cached.chatDigest)) return;
            AtomicFiles.replace(path, bytes);
            cached.chatDigest = digest;
        } catch (IOException | RuntimeException error) {
            // The replay cache is disposable: a chat write never fails the transaction that changed business data.
            SystemMod.LOGGER.warn("Unable to write conversation cache for {}", player, error);
        }
    }

    private static <T> byte[] encode(Codec<T> codec, T value, UUID player) throws IOException {
        return FileAccess.NBT_COMPRESSED.encode(codec, value).orElseThrow(() -> new IOException("Unable to encode agent store: " + player));
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private void prune(Conversation conversation) {
        // Chat retention prunes replay caches only; reward, order and event records keep their own lifetimes.
        long cutoff = System.currentTimeMillis() - config().conversationDays() * 86400000L;
        conversation.history.removeIf(message -> message.time() < cutoff);
        while (conversation.history.size() > config().conversationEntries()) conversation.history.removeFirst();
        Set<String> retainedRequests = new HashSet<>();
        conversation.history.forEach(message -> retainedRequests.add(message.requestId()));
        conversation.requests.keySet().removeIf(key -> !retainedRequests.contains(key));
        conversation.requestInputs.keySet().removeIf(key -> !retainedRequests.contains(key));
        while (conversation.requests.size() > 128) {
            String key = conversation.requests.keySet().iterator().next();
            conversation.requests.remove(key);
            conversation.requestInputs.remove(key);
        }
    }

    @Override
    public void delete(UUID player) {
        this.whileOpen(() -> {
            ReentrantLock lock = this.lock(player);
            lock.lock();
            try {
                Files.deleteIfExists(this.playerRoot.resolve(player + SUFFIX + ".tmp"));
                Files.deleteIfExists(this.playerRoot.resolve(player + SUFFIX));
                Files.deleteIfExists(this.chatRoot.resolve(player + SUFFIX + ".tmp"));
                Files.deleteIfExists(this.chatRoot.resolve(player + SUFFIX));
                this.loaded.remove(player);
            } catch (IOException e) {
                throw storageFailure("delete", this.playerRoot.resolve(player + SUFFIX), e);
            } finally {
                lock.unlock();
            }
            return null;
        });
    }

    @Override
    public List<UUID> players() {
        return this.whileOpen(this::listPlayers);
    }

    private List<UUID> listPlayers() {
        try (Stream<Path> files = Files.list(this.playerRoot)) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).filter(name -> name.endsWith(SUFFIX))
                    .map(name -> UUID.fromString(name.substring(0, name.length() - SUFFIX.length()))).toList();
        } catch (IOException | IllegalArgumentException e) {
            throw storageFailure("list", this.playerRoot, e);
        }
    }

    private static AgentException storageFailure(String operation, Path path, Exception error) {
        SystemMod.LOGGER.error("Unable to {} agent store {}", operation, path, error);
        return new AgentException(AgentFailure.STORAGE_UNAVAILABLE, error);
    }

    private <T> T whileOpen(Supplier<T> work) {
        this.lifecycle.readLock().lock();
        try {
            if (this.closed) throw new AgentException(AgentFailure.STORAGE_UNAVAILABLE);
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
            this.loaded.forEach((player, cached) -> {
                if (!cached.dirty) return;
                try {
                    // Pending module mutations only touch the business file; the conversation is written per transaction.
                    this.saveBusiness(player, cached.data);
                } catch (Exception error) {
                    SystemMod.LOGGER.error("Unable to flush module storage for {} during shutdown", player, error);
                }
            });
            this.loaded.clear();
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

    private static final class CachedPlayer {
        private volatile PlayerData data;
        private boolean dirty;
        private byte[] chatDigest;

        private CachedPlayer(PlayerData data) {
            this.data = data;
        }
    }
}
