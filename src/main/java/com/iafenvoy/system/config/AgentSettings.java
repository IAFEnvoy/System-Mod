package com.iafenvoy.system.config;

import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.util.ObjectCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.net.URI;
import java.time.ZoneId;
import java.util.*;

/**
 * Server-private configuration, unrelated to Minecraft's data-pack registries.
 */
public final class AgentSettings {
    public static final Codec<AgentSettings> CODEC = ObjectCodec.lenientOf(AgentSettings::new, AgentSettings::validate)
            .field("model", Codec.STRING, v -> v.model, (v, x) -> v.model = x)
            .field("baseUrl", Codec.STRING, v -> v.baseUrl, (v, x) -> v.baseUrl = x)
            .field("apiKeyEnvironment", Codec.STRING, v -> v.apiKeyEnvironment, (v, x) -> v.apiKeyEnvironment = x)
            .field("providerType", Codec.STRING, v -> v.providerType, (v, x) -> v.providerType = x)
            .field("thinkingEnabled", Codec.BOOL, v -> v.thinkingEnabled, (v, x) -> v.thinkingEnabled = x)
            .field("reasoningEffort", Codec.STRING, v -> v.reasoningEffort, (v, x) -> v.reasoningEffort = x)
            .field("apiKeyRequired", Codec.BOOL, v -> v.apiKeyRequired, (v, x) -> v.apiKeyRequired = x)
            .field("jsonMode", Codec.BOOL, v -> v.jsonMode, (v, x) -> v.jsonMode = x)
            .field("outputTokenParameter", Codec.STRING, v -> v.outputTokenParameter, (v, x) -> v.outputTokenParameter = x)
            .field("timeZone", Codec.STRING, v -> v.timeZone, (v, x) -> v.timeZone = x)
            .field("modelCallsPerPlayerDay", Codec.INT, v -> v.modelCallsPerPlayerDay, (v, x) -> v.modelCallsPerPlayerDay = x)
            .field("concurrentModels", Codec.INT, v -> v.concurrentModels, (v, x) -> v.concurrentModels = x)
            .field("routingPolicy", ExecutionPolicy.CODEC, v -> v.routingPolicy, (v, x) -> v.routingPolicy = x)
            .field("semantic", SemanticSettings.CODEC, v -> v.semantic, (v, x) -> v.semantic = x)
            .field("policies", Codec.unboundedMap(Codec.STRING, ExecutionPolicy.CODEC).xmap(LinkedHashMap::new, value -> value), v -> v.policies, (v, x) -> v.policies = x)
            .field("rewards", PlayerData.Reward.CODEC.listOf().xmap(ArrayList::new, List::copyOf), v -> v.rewards, (v, x) -> v.rewards = x)
            .field("advancementRules", EventRule.CODEC.listOf().xmap(ArrayList::new, List::copyOf), v -> v.advancementRules, (v, x) -> v.advancementRules = x)
            .field("shopOffers", CommerceCatalog.Offer.CODEC.listOf().xmap(ArrayList::new, List::copyOf), v -> v.shopOffers, (v, x) -> v.shopOffers = x)
            .codec();

    public String model = "deepseek-chat";
    public String baseUrl = "https://api.deepseek.com";
    public String apiKeyEnvironment = "SYSTEM_DEEPSEEK_API_KEY";
    public String providerType = "auto";
    public boolean thinkingEnabled = false;
    public String reasoningEffort = "high";
    public boolean apiKeyRequired = true;
    public boolean jsonMode = true;
    public String outputTokenParameter = "max_tokens";
    public String timeZone = "Asia/Shanghai";
    public int modelCallsPerPlayerDay = 100;
    public int concurrentModels = 4;
    public ExecutionPolicy routingPolicy = new ExecutionPolicy(1, 0, 4, 0, 16384, 512, 32768, 14500, 4000);
    public SemanticSettings semantic = new SemanticSettings();
    public Map<String, ExecutionPolicy> policies = new LinkedHashMap<>();
    public List<PlayerData.Reward> rewards = new ArrayList<>(List.of(
            new PlayerData.Reward("food", "Food supplies", List.of(new PlayerData.Item("minecraft:bread", 6))),
            new PlayerData.Reward("mining", "Mining supplies", List.of(new PlayerData.Item("minecraft:torch", 16))),
            new PlayerData.Reward("building", "Building supplies", List.of(new PlayerData.Item("minecraft:oak_log", 12)))));
    public List<EventRule> advancementRules = new ArrayList<>();
    public List<CommerceCatalog.Offer> shopOffers = new ArrayList<>(List.of(new CommerceCatalog.Offer(
            "system:torch_bundle", "Torch bundle", List.of(new PlayerData.Item("minecraft:torch", 4)),
            new CommerceCatalog.Price("ITEMS", List.of(new PlayerData.Item("minecraft:cobblestone", 16)), 0))));

    public record EventRule(String id, String advancementId, TypedPayload<?> call) {
        public static final Codec<EventRule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.lenientOptionalFieldOf("id", "").forGetter(EventRule::id),
                Codec.STRING.lenientOptionalFieldOf("advancementId", "").forGetter(EventRule::advancementId),
                ResultTypes.ACTIONS.mapCodec().forGetter(EventRule::call)
        ).apply(i, EventRule::new));

        public String moduleId() { return this.call.type().id().toString(); }
    }

    public URI chatCompletionsEndpoint() {
        if (this.baseUrl == null || this.baseUrl.length() > 2048) throw new IllegalArgumentException("Invalid model baseUrl");
        URI endpoint = URI.create(this.baseUrl);
        String host = endpoint.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        if (host == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !("https".equalsIgnoreCase(endpoint.getScheme()) || "http".equalsIgnoreCase(endpoint.getScheme()) && loopback)
                || endpoint.getPort() < -1 || endpoint.getPort() == 0 || endpoint.getPort() > 65535)
            throw new IllegalArgumentException("A fixed HTTPS model baseUrl or loopback HTTP endpoint is required");
        String base = this.baseUrl.replaceAll("/+$", "");
        return URI.create(base + "/chat/completions");
    }

    public boolean isDeepSeek() {
        if ("deepseek".equals(this.providerType)) return true;
        if (!"auto".equals(this.providerType)) return false;
        String host = this.chatCompletionsEndpoint().getHost().toLowerCase(Locale.ROOT);
        return host.equals("deepseek.com") || host.endsWith(".deepseek.com");
    }

    public void validate() {
        this.chatCompletionsEndpoint();
        if (this.model == null || this.model.isBlank() || this.model.length() > 128 || this.apiKeyEnvironment == null || !this.apiKeyEnvironment.matches("[A-Z0-9_]{1,128}")
                || !("max_tokens".equals(this.outputTokenParameter) || "max_completion_tokens".equals(this.outputTokenParameter))
                || !("auto".equals(this.providerType) || "openai".equals(this.providerType) || "deepseek".equals(this.providerType))
                || this.reasoningEffort == null || !this.reasoningEffort.matches("|[a-z][a-z0-9_-]{0,31}"))
            throw new IllegalArgumentException("Invalid model configuration");
        ZoneId.of(this.timeZone);
        if (this.modelCallsPerPlayerDay < 1 || this.modelCallsPerPlayerDay > 10000 || this.concurrentModels < 1 || this.concurrentModels > 16
                || this.rewards == null || this.rewards.size() > 16 || this.policies == null || this.routingPolicy == null || this.semantic == null || this.advancementRules == null
                || this.advancementRules.size() > 128) throw new IllegalArgumentException("Invalid agent limits");
        CommerceCatalog.create(this.rewards, this.shopOffers);
        this.semantic.validate();
        Set<String> ruleIds = new HashSet<>();
        for (EventRule rule : this.advancementRules) {
            if (!rule.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !ruleIds.add(rule.id())
                    || !rule.advancementId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || rule.call().json().toString().length() > 4096)
                throw new IllegalArgumentException("Invalid advancement rule");
        }
    }
}
