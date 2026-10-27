package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.SystemMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.TimeUnit;

/**
 * One PCM producer: prefer native playback, then consume the same stream through raw OpenAL.
 */
final class SpeechPlayback implements AutoCloseable {
    private final SpeechSound sound;
    private final int volume;
    private SpeechState state = SpeechState.STARTING;
    private boolean attempted;
    private long startDeadline;
    private OpenAlSpeechPlayer direct;

    SpeechPlayback(SpeechSound sound, int volume) {
        this.sound = sound;
        this.volume = volume;
    }

    SpeechState tick() {
        if (this.state == SpeechState.FINISHED || this.state == SpeechState.FAILED || this.state == SpeechState.MUTED)
            return this.state;
        Minecraft minecraft = Minecraft.getInstance();
        float gain = this.volume / 100.0F * minecraft.options.getFinalSoundSourceVolume(SoundSource.VOICE);
        if (gain <= 0) {
            this.close();
            return this.state = SpeechState.MUTED;
        }
        if (minecraft.isPaused() && this.direct == null) {
            this.startDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            return this.state;
        }
        if (!this.attempted) {
            this.attempted = true;
            this.startDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            try {
                if (minecraft.getSoundManager().play(this.sound) == SoundEngine.PlayResult.NOT_STARTED)
                    this.fallback(gain);
            } catch (RuntimeException error) {
                this.fallback(gain);
            }
        }
        if (this.direct != null) {
            try {
                if (!this.direct.tick(gain, minecraft.isPaused())) this.close();
            } catch (RuntimeException error) {
                this.close();
                this.state = SpeechState.FAILED;
            }
        } else if (this.state == SpeechState.STARTING || this.state == SpeechState.PLAYING_NATIVE) {
            if (this.sound.nativeStarted()) {
                this.state = SpeechState.PLAYING_NATIVE;
                if (!minecraft.getSoundManager().isActive(this.sound)) this.close();
            } else if (this.sound.nativeFailed() || !minecraft.getSoundManager().isActive(this.sound)
                    || System.nanoTime() >= this.startDeadline) this.fallback(gain);
        }
        return this.state;
    }

    private void fallback(float gain) {
        // Invalidate late stream callbacks before starting an independent source, preventing double playback.
        this.sound.cancel();
        Minecraft.getInstance().getSoundManager().stop(this.sound);
        SystemMod.LOGGER.debug("Native speech did not start; trying the PCM stream through OpenAL");
        try {
            this.direct = new OpenAlSpeechPlayer(this.sound, gain);
            this.state = SpeechState.PLAYING_OPENAL;
        } catch (RuntimeException error) {
            this.state = SpeechState.FAILED;
            SystemMod.LOGGER.warn("Unable to start OpenAL speech playback ({})", error.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        this.sound.cancel();
        Minecraft.getInstance().getSoundManager().stop(this.sound);
        if (this.direct != null) this.direct.close();
        this.direct = null;
        this.state = SpeechState.FINISHED;
    }
}
