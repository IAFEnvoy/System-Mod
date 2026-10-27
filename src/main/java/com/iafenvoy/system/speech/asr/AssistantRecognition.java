package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.speech.AsrProviders;
import com.iafenvoy.system.speech.SpeechException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Each UI owner supplies its lifetime; callbacks cannot escape that owner or the original connection.
 */
public final class AssistantRecognition {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
        Thread thread = new Thread(runnable, "system-recognition");
        thread.setDaemon(true);
        return thread;
    });

    static {
        WORKER.allowCoreThreadTimeOut(true);
    }

    private AssistantRecognition() {
    }

    public static void configure(RecognitionConfig next) throws Exception {
        if (!RecognitionConfig.REGIONS.contains(next.region())) throw new SpeechException("region");
        if (next.enabled()) {
            try (AsrProvider provider = AsrProviders.create(next.provider())) {
                provider.validate(next.request());
            }
        }
        RecognitionConfig.INSTANCE.set(next);
    }

    public static void validate() throws SpeechException {
        RecognitionConfig config = RecognitionConfig.INSTANCE.get();
        try (AsrProvider provider = AsrProviders.create(config.provider())) {
            provider.validate(config.request());
        }
    }

    public static final class Session implements AutoCloseable {
        private final Minecraft minecraft = Minecraft.getInstance();
        private final BooleanSupplier owner;
        private final ClientPacketListener connection = this.minecraft.getConnection();
        private final RecognitionRecording microphone = new RecognitionRecording();
        private final Consumer<String> result;
        private Future<?> pending;
        private volatile boolean closed;
        private volatile boolean stopping;
        private String status = "connecting";
        private String intermediate = "";
        private boolean finished;

        public Session(BooleanSupplier owner, Consumer<String> result) {
            this.owner = owner;
            this.result = result;
        }

        public void start() {
            String providerId;
            AsrProvider.Request request;
            // Freeze this call's wire parameters, not a second copy of the shared configuration.
            synchronized (RecognitionConfig.INSTANCE) {
                providerId = RecognitionConfig.INSTANCE.get().provider();
                request = RecognitionConfig.INSTANCE.get().request();
            }
            WORKER.purge();
            this.pending = WORKER.submit(() -> {
                RecognitionUsageTracker accounting = new RecognitionUsageTracker(providerId, request.region());
                try (AsrProvider provider = AsrProviders.create(providerId); RecognitionRecording capture = this.microphone) {
                    provider.validate(request);
                    capture.open();
                    if (this.closed || Thread.currentThread().isInterrupted()) return;
                    this.update(() -> this.status = this.stopping ? "recognizing" : "recording");
                    accounting.begin();
                    String recognized = provider.recognize(request, new AsrProvider.AudioSource() {
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
                if (this.active()) action.run();
            });
        }

        public boolean active() {
            return !this.closed && this.connection != null && this.minecraft.getConnection() == this.connection && this.owner.getAsBoolean();
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
