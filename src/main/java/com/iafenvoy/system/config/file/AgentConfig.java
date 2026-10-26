package com.iafenvoy.system.config.file;

import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.util.codec.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.time.ZoneId;
import java.util.Map;

/**
 * Execution limits only; content and other configuration groups have independent sources.
 */
public record AgentConfig(String timeZone, int modelCallsPerPlayerDay, int concurrentModels,
                          ExecutionPolicy routingPolicy, Map<String, ExecutionPolicy> policies) {
    public static final String FILE_NAME = "system-agent.json";
    private static final ExecutionPolicy DEFAULT_ROUTING = new ExecutionPolicy(1, 0, 4, 0, 16384, 512, 32768, 14500, 4000);
    public static final Codec<AgentConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.comapFlatMap(value -> CodecIO.mapOrError(() -> ZoneId.of(value).getId()), value -> value)
                    .lenientOptionalFieldOf("timeZone", "Asia/Shanghai").forGetter(AgentConfig::timeZone),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("modelCallsPerPlayerDay", 100).forGetter(AgentConfig::modelCallsPerPlayerDay),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("concurrentModels", 4).forGetter(AgentConfig::concurrentModels),
            ExecutionPolicy.CODEC.lenientOptionalFieldOf("routingPolicy", DEFAULT_ROUTING).forGetter(AgentConfig::routingPolicy),
            Codec.unboundedMap(Codec.STRING, ExecutionPolicy.CODEC).lenientOptionalFieldOf("policies", Map.of()).forGetter(AgentConfig::policies)
    ).apply(i, AgentConfig::new));
    public static final ConfigInstance<AgentConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new AgentConfig("Asia/Shanghai", 100, 4, DEFAULT_ROUTING, Map.of()));

    public AgentConfig {
        policies = Map.copyOf(policies);
    }
}
