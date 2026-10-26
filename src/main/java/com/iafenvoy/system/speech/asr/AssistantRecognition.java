package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.config.RecognitionSettings;
import com.iafenvoy.system.speech.AsrProviders;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.screen.gui.AssistantInputScreen;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Each input screen owns one microphone/request session; callbacks cannot escape that screen or connection. */
public final class AssistantRecognition {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
        Thread thread = new Thread(runnable, "system-recognition");
        thread.setDaemon(true);
        return thread;
    });
    private static RecognitionSettings settings;

    static {
        WORKER.allowCoreThreadTimeOut(true);
    }

    private AssistantRecognition() {
    }

    public static RecognitionSettings settings() {
        if (settings == null) {
            try {
                settings = RecognitionSettings.load();
            } catch (Exception error) {
                settings = RecognitionSettings.defaults();
                SystemMod.LOGGER.warn("Unable to load local recognition configuration");
            }
        }
        return settings;
    }

    public static void configure(RecognitionSettings next) throws Exception {
        if (!RecognitionSettings.REGIONS.contains(next.region())) throw new SpeechException("region");
        if (next.enabled()) {
            try (AsrProvider provider = AsrProviders.create(next.provider())) {
                provider.validate(next.request());
            }
        }
        next.save();
        settings = next;
    }

    public static final class Session implements AutoCloseable {
        private final Minecraft minecraft = Minecraft.getInstance();
        private final AssistantInputScreen owner;
        private final ClientPacketListener connection = this.minecraft.getConnection();
        private final RecognitionRecording microphone = new RecognitionRecording();
        private final Consumer<String> result;
        private Future<?> pending;
        private volatile boolean closed;
        private boolean stopping;
        private String status = "connecting";
        private String intermediate = "";
        private boolean finished;

        public Session(AssistantInputScreen owner, Consumer<String> result) {
            this.owner = owner;
            this.result = result;
        }

        public void start() {
            RecognitionSettings current = settings();
            WORKER.purge();
            this.pending = WORKER.submit(() -> {
                RecognitionUsageTracker accounting = new RecognitionUsageTracker(current);
                try (AsrProvider provider = AsrProviders.create(current.provider()); RecognitionRecording capture = this.microphone) {
                    provider.validate(current.request());
                    capture.open();
                    if (this.closed || Thread.currentThread().isInterrupted()) return;
                    this.update(() -> this.status = this.stopping ? "recognizing" : "recording");
                    accounting.begin();
                    String recognized = provider.recognize(current.request(), new AsrProvider.AudioSource() {
                        @Override
                        public void start() throws Exception {
                            capture.start();
                        }

                        @Override
                        public int read(byte[] buffer) throws Exception {
                            int count = capture.read(buffer);
                            if (count < 0) Session.this.update(() -> Session.this.status = "recognizing");
                            return count;
                        }
                    }, text -> this.update(() -> this.intermediate = text));
                    accounting.complete(true);
                    this.update(() -> {
                        this.finished = true;
                        this.status = recognized.isBlank() ? "empty" : "done";
                        if (!recognized.isBlank()) this.result.accept(recognized);
                    });
                } catch (Exception error) {
                    String code = error instanceof SpeechException speech ? speech.code()
                            : error instanceof TimeoutException ? "timeout" : "network";
                    this.update(() -> {
                        this.finished = true;
                        this.status = code;
                    });
                } finally {
                    accounting.complete(false);
                }
            });
        }

        private void update(Runnable action) {
            this.minecraft.execute(() -> {
                if (!this.closed && this.minecraft.screen == this.owner && this.minecraft.getConnection() == this.connection) action.run();
            });
        }

        public String status() {
            return this.status;
        }

        public String intermediate() {
            return this.intermediate;
        }

        public boolean finished() {
            return this.finished;
        }

        public void finish() {
            if (this.closed || this.finished || this.stopping) return;
            this.stopping = true;
            this.microphone.finish();
            this.status = "recognizing";
        }

        @Override
        public void close() {
            this.closed = true;
            if (this.pending != null) this.pending.cancel(true);
            this.microphone.close();
            WORKER.purge();
        }
    }
}
