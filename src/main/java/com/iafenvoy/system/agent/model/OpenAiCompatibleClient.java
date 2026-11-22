package com.iafenvoy.system.agent.model;

import com.iafenvoy.system.config.credential.builtin.DashScopeCredential;
import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.iafenvoy.system.config.credential.builtin.OpenAiCredential;
import com.iafenvoy.system.config.file.ModelConfig;

public final class OpenAiCompatibleClient extends com.iafenvoy.assistant.model.OpenAiCompatibleClient implements ModelClient {
    private final String defaultUrl;
    private final String defaultModel;
    private final String protocol;

    public OpenAiCompatibleClient() {
        this("", "", "", "auto");
    }

    public OpenAiCompatibleClient(DeepSeekCredential credential) {
        this(credential.getApiKey(), "https://api.deepseek.com", "deepseek-chat", "deepseek");
    }

    public OpenAiCompatibleClient(OpenAiCredential credential) {
        this(credential.getApiKey(), "https://api.openai.com/v1", "", "openai");
    }

    public OpenAiCompatibleClient(DashScopeCredential credential) {
        this(credential.getApiKey(), "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "openai");
    }

    private OpenAiCompatibleClient(String key, String defaultUrl, String defaultModel, String protocol) {
        super(ModelConfig.INSTANCE.get().settings(), key);
        this.defaultUrl = defaultUrl;
        this.defaultModel = defaultModel;
        this.protocol = protocol;
    }

    @Override
    public ModelConfig defaults(ModelConfig current) {
        return new ModelConfig(this.defaultModel.isBlank() ? current.model() : this.defaultModel,
                this.defaultUrl.isBlank() ? current.baseUrl() : this.defaultUrl, this.protocol,
                current.thinkingEnabled(), current.reasoningEffort(), current.apiKeyRequired(), current.jsonMode(), current.outputTokenParameter());
    }
}
