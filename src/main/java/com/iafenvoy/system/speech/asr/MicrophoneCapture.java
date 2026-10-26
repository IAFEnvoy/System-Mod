package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.speech.SpeechException;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.*;

/** Bounded in-memory capture; device creation and reads run exclusively on the recognition worker. */
final class MicrophoneCapture implements RecognitionCapture {
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);
    private TargetDataLine line;
    private boolean stopped;
    private boolean closed;
    private long deadline;
    private int remaining = 16000 * 2 * 60;

    @Override
    public synchronized void open() throws SpeechException {
        if (this.closed) throw new SpeechException("cancelled");
        try {
            this.line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FORMAT));
            this.line.open(FORMAT, 6400);
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException error) {
            if (this.line != null) this.line.close();
            this.line = null;
            throw new SpeechException("microphone");
        }
    }

    @Override
    public synchronized void start() throws SpeechException {
        if (this.closed || this.stopped || this.line == null) throw new SpeechException("cancelled");
        this.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        this.line.start();
    }

    @Override
    public int read(byte[] buffer) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        synchronized (this) {
            if (this.closed || this.remaining == 0 || System.nanoTime() >= this.deadline) return -1;
            int count = Math.min(Math.min(this.line.available(), buffer.length), this.remaining) & ~1;
            if (count > 0) {
                int read = this.line.read(buffer, 0, count);
                this.remaining -= read;
                return read;
            }
            if (this.stopped) return -1;
        }
        // Return control regularly so a remote failure can end capture even if the device has no frames.
        TimeUnit.MILLISECONDS.sleep(10);
        return 0;
    }

    @Override
    public synchronized void finish() {
        this.stopped = true;
        if (this.line != null) this.line.stop();
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        if (this.line != null) {
            this.line.stop();
            this.line.close();
            this.line = null;
        }
    }
}
