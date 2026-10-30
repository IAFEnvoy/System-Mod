package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.SystemMod;
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
    private static final ScheduledThreadPoolExecutor TIMEOUTS = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "system-recognition-timeouts");
        thread.setDaemon(true);
        return thread;
    });

    static {
        WORKER.allowCoreThreadTimeOut(true);
        TIMEOUTS.setRemoveOnCancelPolicy(true);
    }

    private AssistantRecognition() {
    }

    public static void configure(RecognitionConfig next) throws Exception {
        if (!RecognitionConfig.REGIONS.contains(next.region())) throw new SpeechException("region");
        // Partial credentials are saved as typed; starting recognition still validates the complete request.
        RecognitionConfig.INSTANCE.set(next);
        if (!RecognitionConfig.INSTANCE.save()) throw new SpeechException("config");
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
        private ScheduledFuture<?> timeout;
        private int timeoutRevision;
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
            this.scheduleTimeout(90);
            try {
                this.pending = WORKER.submit(() -> {
                    RecognitionUsageTracker accounting = new RecognitionUsageTracker(providerId, request.region());
                    try (AsrProvider provider = AsrProviders.create(providerId); RecognitionRecording capture = this.microphone) {
                        provider.validate(request);
                        capture.open();
                        if (this.closed || Thread.currentThread().isInterrupted()) return;
                        this.update(() -> this.status = this.stopping ? "recognizing" : "recording");
                        String recognized = provider.recognize(request, new AsrProvider.AudioSource() {
                            @Override
                            public void start() throws Exception {
                                capture.start();
                            }

                            @Override
                            public int read(byte[] buffer) throws Exception {
                                int count = capture.read(buffer);
                                if (count < 0) Session.this.update(Session.this::finish);
                                return count;
                            }
                        }, text -> this.update(() -> this.intermediate = text), accounting::begin);
                        accounting.complete(true);
                        this.update(() -> {
                            this.complete();
                            this.status = recognized.isBlank() ? "empty" : "done";
                            if (!recognized.isBlank()) this.result.accept(recognized);
                        });
                    } catch (Exception | LinkageError error) {
                        String code = error instanceof SpeechException speech ? speech.code()
                                : error instanceof TimeoutException ? "timeout" : "network";
                        // Upstream messages can include credentials; log only the local code and exception type.
                        if (!this.closed)
                            SystemMod.LOGGER.warn("Recognition failed: {} ({})", code, error.getClass().getSimpleName());
                        this.update(() -> {
                            this.complete();
                            this.status = code;
                        });
                    } finally {
                        accounting.complete(false);
                    }
                });
            } catch (RejectedExecutionException error) {
                this.complete();
                this.status = "busy";
            }
        }

        private void update(Runnable action) {
            this.minecraft.execute(() -> {
                if (this.active() && !this.finished) action.run();
            });
        }

        private void complete() {
            this.finished = true;
            if (this.timeout != null) this.timeout.cancel(false);
        }

        private void scheduleTimeout(int seconds) {
            if (this.timeout != null) this.timeout.cancel(false);
            int revision = ++this.timeoutRevision;
            // The UI must finish even if a capture backend or provider never returns to its own deadline.
            this.timeout = TIMEOUTS.schedule(() -> this.update(() -> {
                if (this.timeoutRevision != revision) return;
                SystemMod.LOGGER.warn("Recognition session timed out ({})", this.status);
                this.complete();
                this.status = "timeout";
                this.cancelPending();
            }), seconds, TimeUnit.SECONDS);
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
            this.scheduleTimeout(15);
        }

        @Override
        public void close() {
            this.closed = true;
            if (this.timeout != null) this.timeout.cancel(false);
            this.cancelPending();
        }

        private void cancelPending() {
            if (this.pending != null) this.pending.cancel(true);
            this.microphone.close();
            WORKER.purge();
        }
    }
}
