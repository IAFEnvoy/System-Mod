package com.iafenvoy.system.speech.deepseek;

import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.speech.tts.TtsProvider;

public final class DeepSeekTtsProvider extends com.iafenvoy.assistant.speech.deepseek.DeepSeekTtsProvider implements TtsProvider {
    public DeepSeekTtsProvider(DeepSeekCredential credential) {
        super(credential.getApiKey());
    }

    @Override
    public Request request(SpeechConfig config, String text, String clientLanguage) {
        return this.request(text, config.region(), config.model(), config.voice(), clientLanguage);
    }

    @Override
    public SpeechConfig defaults(SpeechConfig current) {
        return current.withVoice(DEFAULT_MODEL, DEFAULT_VOICE);
    }
}
