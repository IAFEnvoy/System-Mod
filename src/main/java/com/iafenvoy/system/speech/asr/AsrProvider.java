package com.iafenvoy.system.speech.asr;

import com.iafenvoy.system.config.file.RecognitionConfig;

public interface AsrProvider extends com.iafenvoy.assistant.speech.asr.AsrProvider {
    Request request(RecognitionConfig config);

    @Override
    default Request request(String region) {
        return this.request(new RecognitionConfig(true, region, false));
    }
}
