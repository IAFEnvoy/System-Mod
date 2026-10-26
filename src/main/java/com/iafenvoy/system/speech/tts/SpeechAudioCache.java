package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.config.ConfigInstance;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Opt-in, worker-only whole-utterance cache. Failures are misses and never trigger an extra API retry.
 */
final class SpeechAudioCache {
    private static final int MAGIC = 0x53504331;
    private static final int HEADER_BYTES = 48;
    private static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 256;

    private SpeechAudioCache() {
    }

    static String key(String provider, TtsProvider.Request request) {
        MessageDigest digest = digest();
        // Length prefixes avoid ambiguous concatenation. Credentials isolate accounts but are never stored.
        for (String value : List.of("pcm-v1", provider, request.apiKey(), request.region(), request.workspace(),
                request.model(), request.voice(), request.language(), request.text())) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static SpeechAudio.@Nullable Snapshot read(String key) {
        Path path = null;
        try {
            if (Thread.currentThread().isInterrupted()) return null;
            path = directory().resolve(key + ".pcm");
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null;
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path)) {
                bytes = input.readNBytes(HEADER_BYTES + MAX_AUDIO_BYTES + 1);
            }
            if (bytes.length <= HEADER_BYTES || bytes.length > HEADER_BYTES + MAX_AUDIO_BYTES)
                throw new IOException("Invalid cached speech size");
            ByteBuffer header = ByteBuffer.wrap(bytes);
            if (header.getInt() != MAGIC) throw new IOException("Unknown cached speech format");
            int sampleRate = header.getInt(), channels = header.getInt(), length = header.getInt();
            byte[] checksum = new byte[32];
            header.get(checksum);
            byte[] pcm = Arrays.copyOfRange(bytes, HEADER_BYTES, bytes.length);
            if (length != pcm.length || !MessageDigest.isEqual(checksum, digest().digest(pcm)))
                throw new IOException("Invalid cached speech checksum");
            SpeechAudio.Snapshot snapshot = new SpeechAudio.Snapshot(sampleRate, channels, pcm);
            try {
                Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
            } catch (IOException ignored) {
                // A read-only entry remains playable; its access timestamp is only an eviction hint.
            }
            return snapshot;
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            if (path != null) remove(path);
            SystemMod.LOGGER.debug("Unable to read cached speech ({})", error.getClass().getSimpleName());
            return null;
        }
    }

    static void write(String key, SpeechAudio.Snapshot snapshot) {
        Path temporary = null;
        try {
            if (Thread.currentThread().isInterrupted()) return;
            Path directory = directory();
            Files.createDirectories(directory);
            byte[] bytes = ByteBuffer.allocate(HEADER_BYTES + snapshot.pcm().length)
                    .putInt(MAGIC).putInt(snapshot.sampleRate()).putInt(snapshot.channels()).putInt(snapshot.pcm().length)
                    .put(digest().digest(snapshot.pcm())).put(snapshot.pcm()).array();
            temporary = Files.createTempFile(directory, "speech-", ".tmp");
            Files.write(temporary, bytes);
            if (Thread.currentThread().isInterrupted()) return;
            evict(directory, bytes.length);
            if (Thread.currentThread().isInterrupted()) return;
            Path destination = directory.resolve(key + ".pcm");
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException error) {
            SystemMod.LOGGER.debug("Unable to store cached speech ({})", error.getClass().getSimpleName());
        } finally {
            if (temporary != null) remove(temporary);
        }
    }

    // Only this cache's hashed entries are managed; unrelated files and symbolic links are untouched.
    private static void evict(Path directory, long incoming) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.pcm")) {
            for (Path path : paths) {
                if (!path.getFileName().toString().matches("[0-9a-f]{64}[.]pcm")) continue;
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isRegularFile())
                    entries.add(new Entry(path, attributes.size(), attributes.lastModifiedTime().toMillis()));
            }
        }
        entries.sort(Comparator.comparingLong(Entry::used));
        long bytes = incoming + entries.stream().mapToLong(Entry::bytes).sum();
        int count = entries.size() + 1;
        for (Entry entry : entries) {
            if (bytes <= MAX_BYTES && count <= MAX_ENTRIES) break;
            Files.deleteIfExists(entry.path());
            bytes -= entry.bytes();
            count--;
        }
    }

    private static Path directory() {
        return ConfigInstance.getConfigFolder().resolve("tts-cache");
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void remove(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException ignored) {
        }
    }

    private record Entry(Path path, long bytes, long used) {
    }
}
