package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.speech.SpeechException;
import java.util.Arrays;
import java.util.concurrent.*;

/** Capture starts before the network handshake; at most 60 seconds of PCM may wait in memory. */
final class RecognitionRecording implements AsrProvider.AudioSource, AutoCloseable {
    private static final ThreadPoolExecutor RECORDER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
        Thread thread = new Thread(runnable, "system-microphone");
        thread.setDaemon(true);
        return thread;
    });
    private final ArrayBlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(3000);
    private final CompletableFuture<Void> opened = new CompletableFuture<>();
    private volatile Future<?> recorder;
    private volatile boolean stopped;
    private volatile boolean closed;
    private volatile boolean ended;
    private volatile Exception failure;
    private byte[] current;
    private int offset;

    static {
        RECORDER.allowCoreThreadTimeOut(true);
    }

    void open() throws Exception {
        RECORDER.purge();
        this.recorder = RECORDER.submit(this::record);
        try {
            this.opened.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            throw new SpeechException("microphone");
        }
    }

    private void record() {
        try (RecognitionCapture device = RecognitionCapture.create()) {
            if (this.closed || this.stopped) throw new SpeechException("cancelled");
            device.open();
            if (this.closed || this.stopped) throw new SpeechException("cancelled");
            device.start();
            this.opened.complete(null);
            byte[] buffer = new byte[640];
            int remaining = 16000 * 2 * 60;
            while (!this.closed && !this.stopped && remaining > 0) {
                int count = device.read(buffer);
                if (count < 0) break;
                if (count == 0) continue;
                if (count > buffer.length || (count & 1) != 0) throw new SpeechException("audio");
                count = Math.min(count, remaining);
                if (!this.frames.offer(Arrays.copyOf(buffer, count))) throw new SpeechException("audio");
                remaining -= count;
            }
        } catch (Exception error) {
            this.failure = error;
            this.opened.completeExceptionally(error);
        } catch (LinkageError error) {
            this.failure = new SpeechException("microphone");
            this.opened.completeExceptionally(this.failure);
        } finally {
            this.ended = true;
        }
    }

    @Override
    public void start() throws SpeechException {
        if (this.closed) throw new SpeechException("cancelled");
    }

    @Override
    public int read(byte[] buffer) throws Exception {
        if (this.closed) throw new InterruptedException();
        if (this.failure != null) throw this.failure;
        if (this.current == null) {
            this.current = this.frames.poll(10, TimeUnit.MILLISECONDS);
            this.offset = 0;
        }
        if (this.current == null) return this.ended ? -1 : 0;
        int count = Math.min(buffer.length, this.current.length - this.offset) & ~1;
        System.arraycopy(this.current, this.offset, buffer, 0, count);
        this.offset += count;
        if (this.offset == this.current.length) this.current = null;
        return count;
    }

    void finish() {
        this.stopped = true;
    }

    @Override
    public void close() {
        this.closed = true;
        Future<?> pending = this.recorder;
        if (pending != null) pending.cancel(true);
        RECORDER.purge();
        this.frames.clear();
    }
}
