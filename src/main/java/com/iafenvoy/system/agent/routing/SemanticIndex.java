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

    private final LexicalIndex<RoutingProfile.Bound> index;

    SemanticIndex(Collection<RoutingProfile.Bound> profiles) throws IOException {
        List<LexicalIndex.Example<RoutingProfile.Bound>> documents = new ArrayList<>();
        for (RoutingProfile.Bound profile : profiles) {
            Map<String, List<String>> texts = new LinkedHashMap<>(profile.examples());
            SemanticConfig.INSTANCE.get().examples().getOrDefault(profile.moduleId().toString(), Map.of()).forEach((language, extra) -> {
                List<String> combined = new ArrayList<>(texts.getOrDefault(language, List.of()));
                combined.addAll(extra);
                texts.put(language, combined);
            });
            for (Map.Entry<String, List<String>> language : texts.entrySet())
                if (SemanticConfig.INSTANCE.get().languages().contains(language.getKey()))
                    for (String text : new LinkedHashSet<>(language.getValue()))
                        documents.add(new LexicalIndex.Example<>(profile, language.getKey(), text));
        }
        this.index = new LexicalIndex<>(documents);
    }

    int size() {
        return this.index.size();
    }

    Ranking search(String text, String language) {
        LexicalIndex.Ranking<RoutingProfile.Bound> ranking = this.index.search(text, language);
        return new Ranking(ranking.matches().stream().map(match -> new Match(match.value(), match.score(), match.coverage(), match.topicsCovered()))
                .sorted(Comparator.comparingDouble(Match::score).reversed().thenComparing(match -> match.profile().moduleId().toString()))
                .limit(5).toList(), ranking.hasTopic(), ranking.writeRequest());
    }
}
