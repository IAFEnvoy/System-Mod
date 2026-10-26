package com.iafenvoy.system.config;

import com.iafenvoy.system.util.ConfigCodecs;
import com.iafenvoy.system.util.ObjectCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/**
 * Lightweight lexical routing; old embedding thresholds are deliberately not reused.
 */
public final class SemanticSettings {
    public static final Codec<SemanticSettings> CODEC = ObjectCodec.lenientOf(SemanticSettings::new, SemanticSettings::validate)
            .field("mode", Codec.STRING.xmap(Mode::valueOf, Mode::name), v -> v.mode, (v, x) -> v.mode = x)
            .field("maxCharacters", Codec.INT, v -> v.maxCharacters, (v, x) -> v.maxCharacters = x)
            .field("minCoverage", Codec.DOUBLE, v -> v.minCoverage, (v, x) -> v.minCoverage = x)
            .field("defaultThreshold", Threshold.CODEC, v -> v.defaultThreshold, (v, x) -> v.defaultThreshold = x)
            .field("languages", Codec.STRING.listOf().xmap(LinkedHashSet::new, List::copyOf), v -> v.languages, (v, x) -> v.languages = x)
            .field("lexicalThresholds", Codec.unboundedMap(Codec.STRING, Threshold.CODEC).xmap(LinkedHashMap::new, value -> value), v -> v.lexicalThresholds, (v, x) -> v.lexicalThresholds = x)
            .field("examples", Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())).xmap(LinkedHashMap::new, value -> value), v -> v.examples, (v, x) -> v.examples = x)
            .field("cacheEntries", Codec.INT, v -> v.cacheEntries, (v, x) -> v.cacheEntries = x)
            .field("cacheSeconds", Codec.INT, v -> v.cacheSeconds, (v, x) -> v.cacheSeconds = x)
            .field("entriesPerPlayer", Codec.INT, v -> v.entriesPerPlayer, (v, x) -> v.entriesPerPlayer = x)
            .codec();

    public static final String ALGORITHM = "lexical-tfidf-v1";

    public enum Mode {OFF, SHADOW, ENFORCE}

    public Mode mode = Mode.ENFORCE;
    public int maxCharacters = 240;
    public double minCoverage = 0.9;
    public Threshold defaultThreshold = new Threshold(0.9, 0.15);
    public Set<String> languages = new LinkedHashSet<>(List.of("zh", "en"));
    public Map<String, Threshold> lexicalThresholds = new LinkedHashMap<>();
    public Map<String, Map<String, List<String>>> examples = new LinkedHashMap<>();
    public int cacheEntries = 1024;
    public int cacheSeconds = 300;
    public int entriesPerPlayer = 32;

    /**
     * Similarity and module margin are evidence, not calibrated probabilities.
     */
    public record Threshold(double score, double margin) {
        public static final Codec<Threshold> CODEC = RecordCodecBuilder.create(i -> i.group(
                ConfigCodecs.checked(Codec.doubleRange(0, 1), Double::isFinite).lenientOptionalFieldOf("score", 0.9).forGetter(Threshold::score),
                ConfigCodecs.checked(Codec.doubleRange(0, 1), Double::isFinite).lenientOptionalFieldOf("margin", 0.15).forGetter(Threshold::margin)
        ).apply(i, Threshold::new));
        public Threshold {
            if (!Double.isFinite(score) || !Double.isFinite(margin) || score < 0 || score > 1 || margin < 0 || margin > 1)
                throw new IllegalArgumentException("Invalid lexical threshold");
        }
    }

    public Threshold threshold(String moduleId, String language) {
        return this.lexicalThresholds.getOrDefault(moduleId + "@" + language, this.lexicalThresholds.getOrDefault(moduleId, this.defaultThreshold));
    }

    public void validate() {
        if (this.mode == null || this.maxCharacters < 16 || this.maxCharacters > 512 || !Double.isFinite(this.minCoverage)
                || this.minCoverage < 0.5 || this.minCoverage > 1 || this.defaultThreshold == null
                || this.languages == null || this.languages.isEmpty() || this.languages.size() > 16
                || this.languages.stream().anyMatch(language -> language == null || !language.matches("[a-z]{2,3}"))
                || this.lexicalThresholds == null || this.lexicalThresholds.size() > 256 || this.examples == null || this.examples.size() > 128
                || this.cacheEntries < 1 || this.cacheEntries > 8192 || this.cacheSeconds < 1 || this.cacheSeconds > 3600
                || this.entriesPerPlayer < 1 || this.entriesPerPlayer > 128)
            throw new IllegalArgumentException("Invalid lexical routing configuration");
        this.lexicalThresholds.forEach((key, value) -> {
            if (key == null || !key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(?:@[a-z]{2,3})?") || value == null)
                throw new IllegalArgumentException("Invalid lexical module threshold");
        });
        int total = 0;
        for (Map.Entry<String, Map<String, List<String>>> module : this.examples.entrySet()) {
            if (module.getKey() == null || !module.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || module.getValue() == null || module.getValue().size() > 16)
                throw new IllegalArgumentException("Invalid routing example module");
            for (Map.Entry<String, List<String>> language : module.getValue().entrySet()) {
                if (language.getKey() == null || !language.getKey().matches("[a-z]{2,3}") || language.getValue() == null
                        || language.getValue().size() > 24 || language.getValue().stream().anyMatch(text ->
                        text == null || text.isBlank() || text.length() > 240))
                    throw new IllegalArgumentException("Invalid routing examples");
                total += language.getValue().size();
            }
        }
        if (total > 1024) throw new IllegalArgumentException("Too many routing examples");
    }
}
