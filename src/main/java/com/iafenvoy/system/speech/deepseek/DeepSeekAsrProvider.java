package com.iafenvoy.system.speech.deepseek;

import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.speech.asr.AsrProvider;

public final class DeepSeekAsrProvider extends com.iafenvoy.assistant.speech.deepseek.DeepSeekAsrProvider implements AsrProvider {
    public DeepSeekAsrProvider(DeepSeekCredential credential) {
        super(credential.getApiKey());
    }

    @Override
    public Request request(RecognitionConfig config) {
        return this.request(config.region());
    }
}
