package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.speech.SpeechException;
import net.neoforged.fml.ModList;

/**
 * Local PCM16/16 kHz capture boundary; optional microphone backends never leak into ASR providers.
 */
public interface RecognitionCapture extends AsrProvider.AudioSource, AutoCloseable {
    void open() throws SpeechException;

    void finish();

    @Override
    void close();

    static boolean voicechatInstalled() {
        return ModList.get().isLoaded("voicechat");
    }

    static RecognitionCapture create() throws SpeechException {
        if (voicechatInstalled()) {
            try {
                return (RecognitionCapture) Class.forName("com.iafenvoy.system.compat.voicechat.VoicechatRecognitionCapture")
                        .getConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError error) {
                SystemMod.LOGGER.warn("Voice chat microphone adapter unavailable ({})", error.getClass().getSimpleName());
                throw new SpeechException("voicechat");
            }
        }
        return new MicrophoneCapture();
    }
}
