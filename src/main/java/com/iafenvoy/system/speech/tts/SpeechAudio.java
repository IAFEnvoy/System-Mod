package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.speech.SpeechException;
import org.jspecify.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded append-only PCM shared by HTTP, native audio and a fresh fallback cursor. No reader waits on I/O.
 */
final class SpeechAudio {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int SLAB_BYTES = 16384;
    private final List<byte[]> slabs = new ArrayList<>();
    private AudioFormat format;
    private int length;
    private boolean finished, cancelled;

    synchronized void append(int sampleRate, int channels, byte[] pcm) throws Exception {
        if (this.cancelled) throw new InterruptedException("Speech cancelled");
        if (this.finished || sampleRate < 8000 || sampleRate > 48000 || channels < 1 || channels > 2
                || this.format != null && (this.format.getSampleRate() != sampleRate || this.format.getChannels() != channels)
                || pcm.length > MAX_BYTES - this.length || this.length + pcm.length > (long) sampleRate * channels * 2 * 180)
            throw new SpeechException("audio");
        if (this.format == null) this.format = new AudioFormat(sampleRate, 16, channels, true, false);
        for (int offset = 0; offset < pcm.length; ) {
            int index = this.length / SLAB_BYTES, position = this.length % SLAB_BYTES;
            if (index == this.slabs.size()) this.slabs.add(new byte[SLAB_BYTES]);
            int size = Math.min(pcm.length - offset, SLAB_BYTES - position);
            System.arraycopy(pcm, offset, this.slabs.get(index), position, size);
            this.length += size;
            offset += size;
        }
    }

    void loadWav(byte[] wav) throws Exception {
        if (wav.length < 12 || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F')
            throw new SpeechException("audio");
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            AudioFormat format = input.getFormat();
            if (!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED) || format.getSampleSizeInBits() != 16
                    || format.isBigEndian() || format.getChannels() < 1 || format.getChannels() > 2
                    || format.getFrameSize() != format.getChannels() * 2 || !Float.isFinite(format.getSampleRate())
                    || format.getSampleRate() != (int) format.getSampleRate()) throw new SpeechException("audio");
            this.append((int) format.getSampleRate(), format.getChannels(), input.readNBytes(MAX_BYTES + format.getFrameSize()));
            this.finish();
        } catch (UnsupportedAudioFileException | IOException | IllegalArgumentException error) {
            throw new SpeechException("audio");
        }
    }

    synchronized void finish() throws SpeechException {
        if (this.cancelled) return;
        if (this.format == null || this.length == 0 || this.length % this.format.getFrameSize() != 0)
            throw new SpeechException("audio");
        this.finished = true;
    }

    synchronized boolean ready() {
        // About 200ms of real audio before starting; short completed utterances can start immediately.
        return !this.cancelled && this.format != null && this.length > 0
                && (this.finished || this.length >= this.format.getSampleRate() * this.format.getFrameSize() * 0.2);
    }

    synchronized AudioFormat format() {
        if (this.format == null) throw new IllegalStateException("Speech format is not available");
        return this.format;
    }

    // Copy only completed PCM; disk I/O happens later on the synthesis worker, outside this lock.
    synchronized @Nullable Snapshot snapshot() {
        if (!this.finished || this.cancelled) return null;
        byte[] pcm = new byte[this.length];
        for (int offset = 0; offset < this.length; offset += SLAB_BYTES)
            System.arraycopy(this.slabs.get(offset / SLAB_BYTES), 0, pcm, offset, Math.min(SLAB_BYTES, this.length - offset));
        return new Snapshot((int) this.format.getSampleRate(), this.format.getChannels(), pcm);
    }

    record Snapshot(int sampleRate, int channels, byte[] pcm) {
        Snapshot {
            if (sampleRate < 8000 || sampleRate > 48000 || channels < 1 || channels > 2 || pcm.length == 0
                    || pcm.length > MAX_BYTES || pcm.length % (channels * 2) != 0
                    || pcm.length > (long) sampleRate * channels * 2 * 180)
                throw new IllegalArgumentException("Invalid cached speech audio");
        }
    }

    synchronized void cancel() {
        this.cancelled = true;
        this.slabs.clear();
        this.length = 0;
    }

    Cursor cursor() {
        return new Cursor();
    }

    final class Cursor implements AutoCloseable {
        private int position;
        private boolean closed;

        @Nullable ByteBuffer read(int expectedSize, boolean keepAlive) {
            synchronized (SpeechAudio.this) {
                if (this.closed || SpeechAudio.this.cancelled) return null;
                if (expectedSize <= 0) return null;
                int frame = SpeechAudio.this.format.getFrameSize();
                int limit = Math.min(expectedSize, (int) (SpeechAudio.this.format.getSampleRate() / 20) * frame);
                int size = Math.min(limit, SpeechAudio.this.length - this.position);
                size -= size % frame;
                if (size <= 0) {
                    if (SpeechAudio.this.finished || !keepAlive || limit < frame) return null;
                    // Vanilla treats an exhausted source as finished. A short silence keeps its queue alive during jitter.
                    return ByteBuffer.allocateDirect(limit - limit % frame).order(ByteOrder.LITTLE_ENDIAN);
                }
                ByteBuffer buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
                while (buffer.hasRemaining()) {
                    int slab = this.position / SLAB_BYTES, offset = this.position % SLAB_BYTES;
                    int count = Math.min(buffer.remaining(), SLAB_BYTES - offset);
                    buffer.put(SpeechAudio.this.slabs.get(slab), offset, count);
                    this.position += count;
                }
                return buffer.flip();
            }
        }

        boolean exhausted() {
            synchronized (SpeechAudio.this) {
                return this.closed || SpeechAudio.this.cancelled || SpeechAudio.this.finished && this.position >= SpeechAudio.this.length;
            }
        }

        @Override
        public void close() {
            synchronized (SpeechAudio.this) {
                this.closed = true;
            }
        }
    }
}
