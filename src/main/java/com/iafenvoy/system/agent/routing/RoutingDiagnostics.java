package com.iafenvoy.system.agent.routing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;

public record RoutingDiagnostics(String algorithm, String mode, String state, int examples, int cacheEntries,
                                 Map<String, Long> counts, Optional<Match> lastMatch, long lastMicros, long totalMicros, long maxMicros) {
    public static final Codec<RoutingDiagnostics> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("algorithm").forGetter(RoutingDiagnostics::algorithm),
            Codec.STRING.fieldOf("mode").forGetter(RoutingDiagnostics::mode),
            Codec.STRING.fieldOf("state").forGetter(RoutingDiagnostics::state),
            Codec.INT.fieldOf("examples").forGetter(RoutingDiagnostics::examples),
            Codec.INT.fieldOf("cacheEntries").forGetter(RoutingDiagnostics::cacheEntries),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("counts").forGetter(RoutingDiagnostics::counts),
            Match.CODEC.optionalFieldOf("lastMatch").forGetter(RoutingDiagnostics::lastMatch),
            Codec.LONG.fieldOf("lastMicros").forGetter(RoutingDiagnostics::lastMicros),
            Codec.LONG.fieldOf("totalMicros").forGetter(RoutingDiagnostics::totalMicros),
            Codec.LONG.fieldOf("maxMicros").forGetter(RoutingDiagnostics::maxMicros)
    ).apply(i, RoutingDiagnostics::new));

    public record Match(String moduleId, String language, double score, double margin, double coverage, String reason) {
        public static final Codec<Match> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("moduleId").forGetter(Match::moduleId),
                Codec.STRING.fieldOf("language").forGetter(Match::language),
                Codec.DOUBLE.fieldOf("score").forGetter(Match::score),
                Codec.DOUBLE.fieldOf("margin").forGetter(Match::margin),
                Codec.DOUBLE.fieldOf("coverage").forGetter(Match::coverage),
                Codec.STRING.fieldOf("reason").forGetter(Match::reason)
        ).apply(i, Match::new));
    }
}
