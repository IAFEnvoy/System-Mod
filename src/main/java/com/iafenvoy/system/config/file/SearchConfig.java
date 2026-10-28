package com.iafenvoy.system.config.file;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.config.ConfigInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.net.URI;

/**
 * Search uses its own endpoint and credentials even when the chat provider changes.
 */
public record SearchConfig(String baseUrl, String apiKeyEnvironment, int maxResponseBytes) {
    public static final String FILE_NAME = "system-search.json";
    public static final Codec<SearchConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.comapFlatMap(value -> MiscCodecs.mapOrError(() -> {
                    endpoint(value, "search");
                    return value;
                }), value -> value).lenientOptionalFieldOf("baseUrl", "https://api.deepseek.com/v1").forGetter(SearchConfig::baseUrl),
            MiscCodecs.NOT_BLANK_STRING.lenientOptionalFieldOf("apiKeyEnvironment", "SYSTEM_DEEPSEEK_API_KEY").forGetter(SearchConfig::apiKeyEnvironment),
            MiscCodecs.checked(Codec.INT, value1 -> value1 > 0).lenientOptionalFieldOf("maxResponseBytes", 4194304).forGetter(SearchConfig::maxResponseBytes)
    ).apply(i, SearchConfig::new));
    public static final ConfigInstance<SearchConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC,
            new SearchConfig("https://api.deepseek.com/v1", "SYSTEM_DEEPSEEK_API_KEY", 4194304));

    public URI searchEndpoint() {
        return endpoint(this.baseUrl, "search");
    }

    public URI fetchEndpoint() {
        return endpoint(this.baseUrl, "fetch");
    }

    private static URI endpoint(String baseUrl, String operation) {
        URI uri = URI.create(baseUrl);
        String host = uri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()) && loopback)
                || uri.getPort() < -1 || uri.getPort() == 0 || uri.getPort() > 65535)
            throw new IllegalArgumentException("A fixed HTTPS search baseUrl or loopback HTTP endpoint is required");
        return URI.create(baseUrl.replaceAll("/+$", "") + "/tools/" + operation);
    }
}
