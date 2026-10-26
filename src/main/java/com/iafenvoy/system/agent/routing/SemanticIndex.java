package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.config.file.SemanticConfig;

import java.io.IOException;
import java.util.*;

/**
 * Small immutable TF-IDF index; OOV features stay in the query norm and coverage denominator.
 */
final class SemanticIndex {
    record Match(RoutingProfile.Bound profile, double score, double coverage, boolean topicsCovered) {
    }

    record Ranking(List<Match> matches, boolean hasTopic, boolean writeRequest) {
        Match first() {
            return this.matches.isEmpty() ? null : this.matches.getFirst();
        }

        double margin() {
            return this.matches.size() < 2 ? 0 : this.matches.getFirst().score() - this.matches.get(1).score();
        }
    }

    private record Document(RoutingProfile.Bound profile, String language, LexicalEncoder.Features features) {
    }

    private record Vector(Map<String, Double> values, double norm, double mass) {
    }

    private record Indexed(RoutingProfile.Bound profile, String language, Set<String> topics, Vector vector) {
    }

    private final LexicalEncoder encoder;
    private final List<Indexed> examples;
    private final Map<String, Map<String, Double>> idf;
    private final Map<String, Double> unknownIdf;

    SemanticIndex(Collection<RoutingProfile.Bound> profiles, SemanticConfig settings) throws IOException {
        this.encoder = new LexicalEncoder();
        List<Document> documents = new ArrayList<>();
        for (RoutingProfile.Bound profile : profiles) {
            Map<String, List<String>> texts = new LinkedHashMap<>(profile.examples());
            settings.examples().getOrDefault(profile.moduleId().toString(), Map.of()).forEach((language, extra) -> {
                List<String> combined = new ArrayList<>(texts.getOrDefault(language, List.of()));
                combined.addAll(extra);
                texts.put(language, combined);
            });
            for (Map.Entry<String, List<String>> language : texts.entrySet())
                if (settings.languages().contains(language.getKey()))
                    for (String text : new LinkedHashSet<>(language.getValue()))
                        documents.add(new Document(profile, language.getKey(), this.encoder.encode(text, language.getKey())));
        }
        Map<String, Map<String, Integer>> frequencies = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Document document : documents) {
            counts.merge(document.language(), 1, Integer::sum);
            Map<String, Integer> terms = frequencies.computeIfAbsent(document.language(), ignored -> new HashMap<>());
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
        this.examples = documents.stream().map(document -> new Indexed(document.profile(), document.language(),
                document.features().topics(), this.vector(document.features(), document.language()))).toList();
    }

    int size() {
        return this.examples.size();
    }

    Ranking search(String text, String language) {
        LexicalEncoder.Features queryFeatures = this.encoder.encode(text, language);
        Vector query = this.vector(queryFeatures, language);
        Map<String, Match> modules = new HashMap<>();
        for (Indexed example : this.examples) {
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
            Match match = new Match(example.profile(), score, query.mass() == 0 ? 0 : Math.min(1, covered / query.mass()),
                    example.topics().containsAll(queryFeatures.topics()));
            Match previous = modules.get(example.profile().moduleId().toString());
            if (previous == null || previous.score() < score)
                modules.put(example.profile().moduleId().toString(), match);
        }
        return new Ranking(modules.values().stream().sorted(Comparator.comparingDouble(Match::score).reversed()
                .thenComparing(match -> match.profile().moduleId().toString())).limit(5).toList(), !queryFeatures.topics().isEmpty(),
                queryFeatures.terms().containsKey("term:write"));
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
