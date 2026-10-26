package com.iafenvoy.system.mixin;

import com.iafenvoy.system.speech.tts.AssistantSpeech;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Release raw speech sources while the old OpenAL context still exists. */
@Mixin(SoundEngine.class)
public abstract class SpeechSoundEngineMixin {
    @Inject(method = {"stopAll", "emergencyShutdown"}, at = @At("HEAD"))
    private void system$stopSpeech(CallbackInfo callback) {
        AssistantSpeech.audioReset();
    }
}
