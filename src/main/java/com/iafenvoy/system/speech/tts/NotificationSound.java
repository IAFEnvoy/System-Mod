package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.speech.SystemSounds;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A local notification cue shares speech volume, category and cancellation ownership.
 */
final class NotificationSound extends AbstractSoundInstance implements AutoCloseable {
    private volatile boolean cancelled;

    NotificationSound(int speechVolume) {
        super(SystemSounds.NOTIFICATION, SoundSource.VOICE, RandomSource.create());
        this.volume = speechVolume / 100.0F * 0.75F;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public boolean canPlaySound() {
        return !this.cancelled;
    }

    // Decoding can finish after the notification closes; stop that late source on the sound thread.
    void nativeSourceStarted(Channel channel) {
        if (this.cancelled) channel.stop();
    }

    @Override
    public void close() {
        this.cancelled = true;
        Minecraft.getInstance().getSoundManager().stop(this);
    }
}
