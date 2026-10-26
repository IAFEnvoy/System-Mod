package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.SystemMod;
import com.mojang.blaze3d.audio.Channel;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge AudioStream hook consumes available PCM without waiting for synthesis or blocking sound updates.
 */
final class SpeechSound extends AbstractSoundInstance {
    private final SpeechAudio audio;
    private volatile boolean cancelled;
    private volatile boolean nativeStarted;
    private volatile boolean nativeFailed;

    SpeechSound(SpeechAudio audio, int volume) {
        super(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "speech"), SoundSource.VOICE, RandomSource.create());
        this.audio = audio;
        this.volume = volume / 100.0F;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    AudioFormat format() {
        return this.audio.format();
    }

    SpeechAudio audio() {
        return this.audio;
    }

    boolean nativeStarted() {
        return this.nativeStarted;
    }

    boolean nativeFailed() {
        return this.nativeFailed;
    }

    void cancel() {
        this.cancelled = true;
    }

    // Called on Minecraft's sound thread after attaching PCM and attempting to start the source.
    void nativeSourceStarted(Channel channel) {
        if (this.cancelled) channel.stop();
        else {
            this.nativeStarted = channel.playing();
            this.nativeFailed = !this.nativeStarted;
        }
    }

    @Override
    public WeighedSoundEvents resolve(@NonNull SoundManager manager) {
        this.sound = new Sound(this.identifier, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
        WeighedSoundEvents event = new WeighedSoundEvents(this.identifier, null);
        event.addSound(this.sound);
        return event;
    }

    @Override
    public @NonNull CompletableFuture<AudioStream> getStream(@NonNull SoundBufferLibrary buffers, @NonNull Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(new AudioStream() {
            private final SpeechAudio.Cursor cursor = SpeechSound.this.audio.cursor();

            @Override
            public @NonNull AudioFormat getFormat() {
                return SpeechSound.this.format();
            }

            @Override
            public @Nullable ByteBuffer read(int expectedSize) {
                if (SpeechSound.this.cancelled) return null;
                return this.cursor.read(expectedSize, true);
            }

            @Override
            public void close() {
                this.cursor.close();
            }
        });
    }
}
