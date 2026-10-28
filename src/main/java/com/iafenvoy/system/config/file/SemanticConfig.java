package com.iafenvoy.system.config.file;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.config.ConfigInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Lightweight lexical routing; old embedding thresholds are deliberately not reused.
 */
public record SemanticConfig(Mode mode, int maxCharacters, double minCoverage, Threshold defaultThreshold,
                             Set<String> languages, Map<String, Threshold> lexicalThresholds,
                             Map<String, Map<String, List<String>>> examples, int cacheEntries, int cacheSeconds,
                             int entriesPerPlayer) {
    public static final String FILE_NAME = "system-semantic.json";
    public static final String ALGORITHM = "lexical-tfidf-v1";
    private static final Codec<Double> UNIT_INTERVAL;

    static {
        Codec<Double> codec = Codec.doubleRange(0, 1);
        UNIT_INTERVAL = MiscCodecs.checked(codec, Double::isFinite);
    }

    private static final Codec<String> LANGUAGE = MiscCodecs.string("[a-z]{2,3}");
    private static final Codec<String> MODULE_ID = MiscCodecs.string("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public static final Codec<SemanticConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mode.CODEC.lenientOptionalFieldOf("mode", Mode.ENFORCE).forGetter(SemanticConfig::mode),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("maxCharacters", 240).forGetter(SemanticConfig::maxCharacters),
            UNIT_INTERVAL.lenientOptionalFieldOf("minCoverage", 0.9).forGetter(SemanticConfig::minCoverage),
            Threshold.CODEC.lenientOptionalFieldOf("defaultThreshold", new Threshold(0.9, 0.15)).forGetter(SemanticConfig::defaultThreshold),
            LANGUAGE.listOf()
                    .xmap(values -> Collections.unmodifiableSet(new LinkedHashSet<>(values)), List::copyOf)
                    .lenientOptionalFieldOf("languages", Set.of("zh", "en")).forGetter(SemanticConfig::languages),
            Codec.unboundedMap(MiscCodecs.string("[a-z0-9_.-]+:[a-z0-9_./-]+(?:@[a-z]{2,3})?"), Threshold.CODEC).lenientOptionalFieldOf("lexicalThresholds", Map.of()).forGetter(SemanticConfig::lexicalThresholds),
            Codec.unboundedMap(MODULE_ID, Codec.unboundedMap(LANGUAGE, MiscCodecs.NOT_BLANK_STRING.listOf())).lenientOptionalFieldOf("examples", Map.of()).forGetter(SemanticConfig::examples),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("cacheEntries", 1024).forGetter(SemanticConfig::cacheEntries),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("cacheSeconds", 300).forGetter(SemanticConfig::cacheSeconds),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("entriesPerPlayer", 32).forGetter(SemanticConfig::entriesPerPlayer)
    ).apply(i, SemanticConfig::new));
    public static final ConfigInstance<SemanticConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new SemanticConfig(Mode.ENFORCE, 240, 0.9, new Threshold(0.9, 0.15), Set.of("zh", "en"), Map.of(), Map.of(), 1024, 300, 32));

    public SemanticConfig {
        languages = Set.copyOf(languages);
        lexicalThresholds = Map.copyOf(lexicalThresholds);
        Map<String, Map<String, List<String>>> copied = new LinkedHashMap<>();
        examples.forEach((module, localized) -> {
            Map<String, List<String>> texts = new LinkedHashMap<>();
            localized.forEach((language, values) -> texts.put(language, List.copyOf(values)));
            copied.put(module, Map.copyOf(texts));
        });
        examples = Map.copyOf(copied);
    }

    /**
     * Similarity and module margin are evidence, not calibrated probabilities.
     */
    public record Threshold(double score, double margin) {
        public static final Codec<Threshold> CODEC = RecordCodecBuilder.create(i -> i.group(
                UNIT_INTERVAL.lenientOptionalFieldOf("score", 0.9).forGetter(Threshold::score),
                UNIT_INTERVAL.lenientOptionalFieldOf("margin", 0.15).forGetter(Threshold::margin)
        ).apply(i, Threshold::new));
    }

    public Threshold threshold(String moduleId, String language) {
        return this.lexicalThresholds.getOrDefault(moduleId + "@" + language, this.lexicalThresholds.getOrDefault(moduleId, this.defaultThreshold));
    }

    public enum Mode implements StringRepresentable {
        OFF, SHADOW, ENFORCE;
        public static final Codec<Mode> CODEC = StringRepresentable.fromValues(Mode::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
