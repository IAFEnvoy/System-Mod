package com.iafenvoy.system.speech.tts;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Bounded OpenAL buffer queue in MC's context; starvation resumes from the same PCM cursor.
 */
final class OpenAlSpeechPlayer implements AutoCloseable {
    private final long context;
    private final SpeechSound sound;
    private final SpeechAudio.Cursor cursor;
    private final Deque<Integer> free = new ArrayDeque<>();
    private final int[] buffers = new int[4];
    private int source;

    OpenAlSpeechPlayer(SpeechSound sound, float volume) {
        RenderSystem.assertOnRenderThread();
        this.context = ALC10.alcGetCurrentContext();
        if (this.context == 0) throw new IllegalStateException("No OpenAL context for speech");
        this.sound = sound;
        this.cursor = sound.audio().cursor();
        try {
            AL10.alGetError();
            this.source = AL10.alGenSources();
            for (int index = 0; index < this.buffers.length; index++) {
                this.buffers[index] = AL10.alGenBuffers();
                this.free.addLast(this.buffers[index]);
            }
            this.checkError();
            AL10.alSourcei(this.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSourcei(this.source, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcef(this.source, AL10.AL_ROLLOFF_FACTOR, 0);
            AL10.alSource3f(this.source, AL10.AL_POSITION, 0, 0, 0);
            this.tick(volume, false);
        } catch (RuntimeException error) {
            this.close();
            throw error;
        }
    }

    boolean tick(float volume, boolean paused) {
        RenderSystem.assertOnRenderThread();
        if (this.source == 0 || ALC10.alcGetCurrentContext() != this.context) return false;
        int processed = AL10.alGetSourcei(this.source, AL10.AL_BUFFERS_PROCESSED);
        for (int index = 0; index < processed; index++) this.free.addLast(AL10.alSourceUnqueueBuffers(this.source));
        while (!this.free.isEmpty()) {
            ByteBuffer pcm = this.cursor.read(Integer.MAX_VALUE, false);
            if (pcm == null) break;
            int buffer = this.free.removeFirst();
            AL10.alBufferData(buffer, this.sound.format().getChannels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16,
                    pcm, (int) this.sound.format().getSampleRate());
            AL10.alSourceQueueBuffers(this.source, buffer);
        }
        AL10.alSourcef(this.source, AL10.AL_GAIN, volume);
        int state = AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE);
        int queued = AL10.alGetSourcei(this.source, AL10.AL_BUFFERS_QUEUED);
        if (paused && state == AL10.AL_PLAYING) AL10.alSourcePause(this.source);
        else if (!paused && queued > 0 && state != AL10.AL_PLAYING) AL10.alSourcePlay(this.source);
        this.checkError();
        return queued > 0 || !this.cursor.exhausted();
    }

    private void checkError() {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) throw new IllegalStateException("OpenAL speech error " + error);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        this.cursor.close();
        // IDs belong to one context; never delete recycled IDs after device/context replacement.
        if (ALC10.alcGetCurrentContext() == this.context) {
            if (this.source != 0) {
                AL10.alSourceStop(this.source);
                AL10.alDeleteSources(this.source);
            }
            for (int buffer : this.buffers) if (buffer != 0) AL10.alDeleteBuffers(buffer);
        }
        this.source = 0;
        Arrays.fill(this.buffers, 0);
        this.free.clear();
    }
}
