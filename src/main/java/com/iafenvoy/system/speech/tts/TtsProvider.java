package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.config.file.SpeechConfig;

public interface TtsProvider extends com.iafenvoy.assistant.speech.tts.TtsProvider {
    Request request(SpeechConfig config, String text, String clientLanguage);

    default SpeechConfig defaults(SpeechConfig current) {
        return current;
    }

    @Override
    default Request request(String text, String region, String model, String voice, String language) {
        SpeechConfig defaults = SpeechConfig.defaults();
        return this.request(new SpeechConfig(true, region, model, voice, language, defaults.volume(),
                defaults.cacheEnabled(), defaults.questRequirements(), defaults.questRewards(),
                defaults.questDescription(), defaults.notificationSoundEnabled()), text, language);
    }
}
