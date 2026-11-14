package com.iafenvoy.system.speech;

import com.iafenvoy.saturn.util.FileAccess;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.data.usage.RecognitionUsage;
import com.iafenvoy.system.data.usage.SpeechUsage;
import com.iafenvoy.system.speech.asr.AsrProvider;
import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Personal voice usage stays on this installation, across server changes and binding resets.
 */
public final class LocalSpeechUsage {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), runnable -> {
        Thread thread = new Thread(runnable, "system-speech-usage");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, Ledger> ACCOUNTS = new ConcurrentHashMap<>();

    static {
        WORKER.allowCoreThreadTimeOut(true);
        // Drain queued writes on JVM shutdown without making the render thread wait for disk I/O.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            WORKER.shutdown();
            try {
                if (!WORKER.awaitTermination(3, TimeUnit.SECONDS)) WORKER.shutdownNow();
            } catch (InterruptedException error) {
                WORKER.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }, "system-speech-usage-shutdown"));
    }

    private LocalSpeechUsage() {
    }

    public static Ledger current() {
        UUID player = Minecraft.getInstance().getUser().getProfileId();
        return ACCOUNTS.computeIfAbsent(player, id -> new Ledger(ConfigInstance.CONFIG_FOLDER.resolve("speech-usage").resolve(id + ".json")));
    }

    public static Snapshot snapshot() {
        return current().snapshot();
    }

    public record Snapshot(SpeechUsage speech, RecognitionUsage recognition) {
        public static final Snapshot EMPTY = new Snapshot(SpeechUsage.EMPTY, RecognitionUsage.EMPTY);
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                SpeechUsage.CODEC.fieldOf("speech").forGetter(Snapshot::speech),
                RecognitionUsage.CODEC.fieldOf("recognition").forGetter(Snapshot::recognition)
        ).apply(i, Snapshot::new));
    }

    /**
     * Call trackers capture their ledger, so late completions cannot be charged to a different account.
     */
    public static final class Ledger {
        private final Path path;
        /**
         * Null until the queued load ran; every update and the load itself commit through one atomic reference.
         */
        private final AtomicReference<Snapshot> value = new AtomicReference<>();

        private Ledger(Path path) {
            this.path = path;
            // Queue loading before any updates; acquiring an account never reads or parses a file.
            WORKER.execute(this::load);
        }

        private void load() {
            try {
                this.value.compareAndSet(null, FileAccess.JSON.loadOrDefault(Snapshot.CODEC, this.path, Snapshot.EMPTY));
            } catch (Exception e) {
                SystemMod.LOGGER.error("Unable to load local speech usage {}", this.path, e);
            }
        }

        public Snapshot snapshot() {
            Snapshot value = this.value.get();
            return value == null ? Snapshot.EMPTY : value;
        }

        public void beginSpeech() {
            this.update(value -> new Snapshot(value.speech().begin(), value.recognition()));
        }

        public void completeSpeech(String provider, String model, String region, TtsProvider.Usage usage, boolean successful) {
            this.update(value -> new Snapshot(value.speech().complete(provider, model, region, usage, successful), value.recognition()));
        }

        public void beginRecognition() {
            this.update(value -> new Snapshot(value.speech(), value.recognition().begin()));
        }

        public void completeRecognition(String provider, String region, AsrProvider.Usage usage, boolean successful) {
            this.update(value -> new Snapshot(value.speech(), value.recognition().complete(provider, region, usage, successful)));
        }

        private void update(UnaryOperator<Snapshot> action) {
            try {
                WORKER.execute(() -> {
                    try {
                        // Read-modify-write on the reference itself, so it does not depend on the pool staying single-threaded.
                        Snapshot updated = this.value.updateAndGet(value -> action.apply(value == null ? Snapshot.EMPTY : value));
                        this.save(updated);
                    } catch (RuntimeException | LinkageError error) {
                        // A throwing action never commits; the last snapshot survives and audio/text are still delivered.
                        SystemMod.LOGGER.error("Unable to update local speech usage {}", this.path, error);
                    }
                });
            } catch (RejectedExecutionException error) {
                SystemMod.LOGGER.warn("Local speech usage update arrived after shutdown");
            }
        }

        /**
         * Saves exactly the snapshot that was just committed, never whatever the reference holds by now.
         */
        private void save(Snapshot value) {
            Path temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
            try {
                Files.createDirectories(this.path.getParent());
                Files.writeString(temporary, CodecIO.write(Snapshot.CODEC, value));
                try {
                    Files.move(temporary, this.path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException error) {
                    Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException error) {
                // Accounting failures never abort synthesis or recognition; the in-memory totals stay available.
                SystemMod.LOGGER.error("Unable to save local speech usage {}", this.path, error);
            }
        }
    }
}
