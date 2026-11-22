package com.iafenvoy.system.registry;

import com.iafenvoy.assistant.credential.CredentialManager;
import com.iafenvoy.assistant.registry.AssistantServices;
import com.iafenvoy.assistant.speech.asr.AsrProvider;
import com.iafenvoy.assistant.speech.tts.TtsProvider;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.model.OpenAiCompatibleClient;
import com.iafenvoy.system.config.credential.builtin.AliyunNlsCredential;
import com.iafenvoy.system.config.credential.builtin.DashScopeCredential;
import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.iafenvoy.system.config.credential.builtin.OpenAiCredential;
import com.iafenvoy.system.search.DeepSeekSearchClient;
import com.iafenvoy.system.search.SearchProvider;
import com.iafenvoy.system.speech.aliyun.AliyunAsrProvider;
import com.iafenvoy.system.speech.aliyun.AliyunTtsProvider;
import com.iafenvoy.system.speech.deepseek.DeepSeekAsrProvider;
import com.iafenvoy.system.speech.deepseek.DeepSeekTtsProvider;

/**
 * Actual service constructors, independently extensible for each use of a credential.
 */
@SuppressWarnings("unused")
public final class SystemServices {
    public static final CredentialManager<ModelClient> MODEL = new CredentialManager<ModelClient>()
            .register(DeepSeekCredential.class, OpenAiCompatibleClient::new)
            .register(OpenAiCredential.class, OpenAiCompatibleClient::new)
            .register(DashScopeCredential.class, OpenAiCompatibleClient::new);
    public static final CredentialManager<SearchProvider> SEARCH = new CredentialManager<SearchProvider>()
            .register(DeepSeekCredential.class, DeepSeekSearchClient::new);
    public static final CredentialManager<TtsProvider> TTS = AssistantServices.TTS
            .register(DeepSeekCredential.class, DeepSeekTtsProvider::new)
            .register(DashScopeCredential.class, AliyunTtsProvider::new);
    public static final CredentialManager<AsrProvider> ASR = AssistantServices.ASR
            .register(DeepSeekCredential.class, DeepSeekAsrProvider::new)
            .register(AliyunNlsCredential.class, AliyunAsrProvider::new);

    private SystemServices() {
    }
}
