package com.iafenvoy.system.speech;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.data.usage.RecognitionUsage;
import com.iafenvoy.system.data.usage.SpeechUsage;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Personal voice usage stays on this installation, across server changes and binding resets.
 */
public final class LocalSpeechUsage {
    private static final Map<UUID, Ledger> ACCOUNTS = new ConcurrentHashMap<>();

    private LocalSpeechUsage() {
    }

    public static Ledger current() {
        UUID player = Minecraft.getInstance().getUser().getProfileId();
        return ACCOUNTS.computeIfAbsent(player, id -> new Ledger(ConfigInstance.getConfigFolder()
                .resolve("speech-usage").resolve(id + ".json")));
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
        private Snapshot value = Snapshot.EMPTY;

        private Ledger(Path path) {
            this.path = path;
            try {
                if (Files.exists(path)) this.value = CodecIO.parse(Snapshot.CODEC, Files.readString(path));
            } catch (IOException | RuntimeException error) {
                SystemMod.LOGGER.error("Unable to load local speech usage {}", path, error);
            }
        }

        public synchronized Snapshot snapshot() {
            return this.value;
        }

        public synchronized void beginSpeech() {
            this.value = new Snapshot(this.value.speech().begin(), this.value.recognition());
            this.save();
        }

        public synchronized void completeSpeech(String provider, String model, String region, TtsProvider.Usage usage, boolean successful) {
            this.value = new Snapshot(this.value.speech().complete(provider, model, region, usage, successful), this.value.recognition());
            this.save();
        }

        public synchronized void beginRecognition() {
            this.value = new Snapshot(this.value.speech(), this.value.recognition().begin());
            this.save();
        }

        public synchronized void completeRecognition(String provider, String region, boolean successful) {
            this.value = new Snapshot(this.value.speech(), this.value.recognition().complete(provider, region, successful));
            this.save();
        }

        private void save() {
            Path temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
            try {
                Files.createDirectories(this.path.getParent());
                Files.writeString(temporary, CodecIO.write(Snapshot.CODEC, this.value));
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
