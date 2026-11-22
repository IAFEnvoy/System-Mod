package com.iafenvoy.system.data.store;

import com.iafenvoy.assistant.storage.AgentStore;
import com.iafenvoy.saturn.util.FileAccess;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.util.AtomicFiles;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Immutable daily snapshots; reads and writes must run on storage workers under the player transaction.
 */
public final class SignInArchive {
    private final Path root;

    public SignInArchive(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private Path path(UUID player, String scope, String day) {
        try {
            if (!UUID.fromString(scope).toString().equals(scope) || !LocalDate.parse(day).toString().equals(day))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException | DateTimeException error) {
            throw new AgentException(AgentFailure.INVALID_INPUT, error);
        }
        return this.root.resolve(player.toString()).resolve(scope).resolve(day.substring(0, 4)).resolve(day + ".dat");
    }

    public Optional<PersistHolder<Reward>> get(AgentStore<PlayerData> store, UUID player, String scope, String day) {
        Path file = this.path(player, scope, day);
        try {
            if (Files.notExists(file)) return Optional.empty();
            return Optional.of(FileAccess.NBT_COMPRESSED.load(store.codec(PersistHolder.codec(Reward.CODEC)), file)
                    .orElseThrow(() -> new IOException("Unreadable sign-in archive " + file)));
        } catch (IOException | RuntimeException error) {
            throw failure("read", file, error);
        }
    }

    public void put(AgentStore<PlayerData> store, UUID player, String scope, String day, PersistHolder<Reward> reward) {
        Path file = this.path(player, scope, day);
        try {
            Codec<PersistHolder<Reward>> codec = store.codec(PersistHolder.codec(Reward.CODEC));
            Optional<PersistHolder<Reward>> previous = this.get(store, player, scope, day);
            if (previous.isPresent()) {
                if (!codec.encodeStart(NbtOps.INSTANCE, previous.get()).getOrThrow().equals(codec.encodeStart(NbtOps.INSTANCE, reward).getOrThrow()))
                    throw new IOException("Conflicting sign-in archive " + file);
                return;
            }
            AtomicFiles.replace(file, FileAccess.NBT_COMPRESSED.encode(codec, reward)
                    .orElseThrow(() -> new IOException("Unable to encode sign-in archive " + file)));
        } catch (IOException | RuntimeException error) {
            throw failure("write", file, error);
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
        SystemMod.LOGGER.error("Unable to {} sign-in archive {}", operation, path, error);
        return new AgentException(AgentFailure.STORAGE_UNAVAILABLE, error);
    }
}
