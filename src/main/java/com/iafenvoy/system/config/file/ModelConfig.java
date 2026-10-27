package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.util.codec.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Server-private Chat Completions connection and provider-specific request options.
 */
public record ModelConfig(String model, String baseUrl, String apiKeyEnvironment, String providerType,
                          boolean thinkingEnabled, String reasoningEffort, boolean apiKeyRequired,
                          boolean jsonMode, String outputTokenParameter) {
    public static final String FILE_NAME = "system-model.json";
    public static final Codec<ModelConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            CodecIO.checked(Codec.STRING, value -> !value.isBlank()).lenientOptionalFieldOf("model", "deepseek-chat").forGetter(ModelConfig::model),
            Codec.STRING.comapFlatMap(value -> CodecIO.mapOrError(() -> {
                endpoint(value);
                return value;
            }), value -> value).lenientOptionalFieldOf("baseUrl", "https://api.deepseek.com").forGetter(ModelConfig::baseUrl),
            Codec.STRING.lenientOptionalFieldOf("apiKeyEnvironment", "SYSTEM_DEEPSEEK_API_KEY").forGetter(ModelConfig::apiKeyEnvironment),
            CodecIO.checked(Codec.STRING, Set.of("auto", "openai", "deepseek")::contains).lenientOptionalFieldOf("providerType", "auto").forGetter(ModelConfig::providerType),
            Codec.BOOL.lenientOptionalFieldOf("thinkingEnabled", false).forGetter(ModelConfig::thinkingEnabled),
            Codec.STRING.lenientOptionalFieldOf("reasoningEffort", "high").forGetter(ModelConfig::reasoningEffort),
            Codec.BOOL.lenientOptionalFieldOf("apiKeyRequired", true).forGetter(ModelConfig::apiKeyRequired),
            Codec.BOOL.lenientOptionalFieldOf("jsonMode", true).forGetter(ModelConfig::jsonMode),
            CodecIO.checked(Codec.STRING, Set.of("max_tokens", "max_completion_tokens")::contains).lenientOptionalFieldOf("outputTokenParameter", "max_tokens").forGetter(ModelConfig::outputTokenParameter)
    ).apply(i, ModelConfig::new));
    public static final ConfigInstance<ModelConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new ModelConfig("deepseek-chat", "https://api.deepseek.com", "SYSTEM_DEEPSEEK_API_KEY", "auto", false, "high", true, true, "max_tokens"));

    public URI chatCompletionsEndpoint() {
        return endpoint(this.baseUrl);
    }

    private static URI endpoint(String baseUrl) {
        URI endpoint = URI.create(baseUrl);
        String host = endpoint.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        if (host == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !("https".equalsIgnoreCase(endpoint.getScheme()) || "http".equalsIgnoreCase(endpoint.getScheme()) && loopback)
                || endpoint.getPort() < -1 || endpoint.getPort() == 0 || endpoint.getPort() > 65535)
            throw new IllegalArgumentException("A fixed HTTPS model baseUrl or loopback HTTP endpoint is required");
        String base = baseUrl.replaceAll("/+$", "");
        return URI.create(base + "/chat/completions");
    }

    public boolean isDeepSeek() {
        if ("deepseek".equals(this.providerType)) return true;
        if (!"auto".equals(this.providerType)) return false;
        String host = this.chatCompletionsEndpoint().getHost().toLowerCase(Locale.ROOT);
        return host.equals("deepseek.com") || host.endsWith(".deepseek.com");
    }
}
