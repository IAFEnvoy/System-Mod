package com.iafenvoy.system.agent.routing;

import java.io.IOException;
import java.util.*;

/**
 * Immutable sparse TF-IDF vectors shared by routing, notification commands and local guide retrieval.
 */
public final class LexicalIndex<T> {
    public record Example<T>(T value, String language, String text) {
    }

    public record Match<T>(T value, double score, double coverage, boolean topicsCovered) {
    }

    public record Ranking<T>(List<Match<T>> matches, boolean hasTopic, boolean writeRequest) {
    }

    private record Document<T>(T value, String language, LexicalEncoder.Features features) {
    }

    private record Vector(Map<String, Double> values, double norm, double mass) {
    }

    private record Indexed<T>(T value, String language, Set<String> topics, Vector vector) {
    }

    private final LexicalEncoder encoder;
    private final List<Indexed<T>> examples;
    private final Map<String, Map<String, Double>> idf;
    private final Map<String, Double> unknownIdf;

    public LexicalIndex(Collection<Example<T>> examples) throws IOException {
        this(examples, true);
    }

    public LexicalIndex(Collection<Example<T>> examples, boolean domainAliases) throws IOException {
        this.encoder = new LexicalEncoder(domainAliases);
        List<Document<T>> documents = examples.stream()
                .map(example -> new Document<>(example.value(), example.language(), this.encoder.encode(example.text(), example.language()))).toList();
        Map<String, Map<String, Integer>> frequencies = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Document<T> document : documents) {
            counts.merge(document.language(), 1, Integer::sum);
            Map<String, Integer> terms = frequencies.computeIfAbsent(document.language(), unused -> new HashMap<>());
            document.features().terms().keySet().forEach(term -> terms.merge(term, 1, Integer::sum));
        }
        Map<String, Map<String, Double>> weights = new HashMap<>();
        Map<String, Double> defaults = new HashMap<>();
        frequencies.forEach((language, terms) -> {
            int count = counts.get(language);
            Map<String, Double> values = new HashMap<>();
            terms.forEach((term, frequency) -> values.put(term, 1 + Math.log((1.0 + count) / (1.0 + frequency))));
            weights.put(language, Map.copyOf(values));
            defaults.put(language, 1 + Math.log(1.0 + count));
        });
        this.idf = Map.copyOf(weights);
        this.unknownIdf = Map.copyOf(defaults);
        this.examples = documents.stream().map(document -> new Indexed<>(document.value(), document.language(),
                document.features().topics(), this.vector(document.features(), document.language()))).toList();
    }

    public int size() {
        return this.examples.size();
    }

    public Ranking<T> search(String text, String language) {
        LexicalEncoder.Features features = this.encoder.encode(text, language);
        Vector query = this.vector(features, language);
        Map<T, Match<T>> values = new LinkedHashMap<>();
        for (Indexed<T> example : this.examples) {
            if (!example.language().equals(language)) continue;
            Vector candidate = example.vector();
            double dot = 0, covered = 0;
            for (Map.Entry<String, Double> term : query.values().entrySet()) {
                Double other = candidate.values().get(term.getKey());
                if (other != null) {
                    dot += term.getValue() * other;
                    covered += term.getValue();
                }
            }
            double score = query.norm() == 0 || candidate.norm() == 0 ? 0 : Math.min(1, dot / (query.norm() * candidate.norm()));
            Match<T> match = new Match<>(example.value(), score, query.mass() == 0 ? 0 : Math.min(1, covered / query.mass()),
                    example.topics().containsAll(features.topics()));
            Match<T> previous = values.get(example.value());
            if (previous == null || previous.score() < score) values.put(example.value(), match);
        }
        return new Ranking<>(values.values().stream().sorted(Comparator.comparingDouble(Match<T>::score).reversed()).toList(),
                !features.topics().isEmpty(), features.terms().containsKey("term:write"));
    }

    private Vector vector(LexicalEncoder.Features features, String language) {
        Map<String, Double> values = new HashMap<>();
        double squared = 0, mass = 0;
        for (Map.Entry<String, Double> feature : features.terms().entrySet()) {
            double frequency = feature.getValue();
            double weight = (frequency < 1 ? frequency : 1 + Math.log(frequency)) * this.idf.getOrDefault(language, Map.of())
                    .getOrDefault(feature.getKey(), this.unknownIdf.getOrDefault(language, 1.0));
            values.put(feature.getKey(), weight);
            squared += weight * weight;
            mass += weight;
        }
        return new Vector(Map.copyOf(values), Math.sqrt(squared), mass);
    }
}
