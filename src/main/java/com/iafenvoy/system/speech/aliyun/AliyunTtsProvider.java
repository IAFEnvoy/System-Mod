package com.iafenvoy.system.speech.aliyun;

import com.iafenvoy.system.config.credential.builtin.DashScopeCredential;
import com.iafenvoy.system.config.file.SpeechConfig;
import com.iafenvoy.system.speech.tts.TtsProvider;

public final class AliyunTtsProvider extends com.iafenvoy.assistant.speech.aliyun.AliyunTtsProvider implements TtsProvider {
    public AliyunTtsProvider(DashScopeCredential credential) {
        super(credential.getApiKey(), credential.getWorkspace());
    }

    @Override
    public Request request(SpeechConfig config, String text, String clientLanguage) {
        return this.request(text, config.region(), config.model(), config.voice(), config.synthesisLanguage(clientLanguage));
    }

    @Override
    public SpeechConfig defaults(SpeechConfig current) {
        return current.withVoice(DEFAULT_MODEL, DEFAULT_VOICE);
    }
}
