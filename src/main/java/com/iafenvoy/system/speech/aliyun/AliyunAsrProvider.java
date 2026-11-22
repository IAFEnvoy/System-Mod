package com.iafenvoy.system.speech.aliyun;

import com.iafenvoy.system.config.credential.builtin.AliyunNlsCredential;
import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.speech.asr.AsrProvider;

public final class AliyunAsrProvider extends com.iafenvoy.assistant.speech.aliyun.AliyunAsrProvider implements AsrProvider {
    public AliyunAsrProvider(AliyunNlsCredential credential) {
        super(credential.getAppKey(), credential.getAccessKeyId(), credential.getAccessKeySecret());
    }

    @Override
    public Request request(RecognitionConfig config) {
        return this.request(config.region());
    }
}
