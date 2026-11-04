package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.config.file.SemanticConfig;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * Builds routing examples and filters shared lexical rankings to available workflows.
 */
final class SemanticIndex {
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

    LexicalIndex.Ranking<RoutingProfile.Bound> search(String text, String language, Predicate<Identifier> available) {
        LexicalIndex.Ranking<RoutingProfile.Bound> ranking = this.index.search(text, language);
        return new LexicalIndex.Ranking<>(ranking.matches().stream()
                .filter(match -> available.test(match.value().moduleId()))
                .sorted(Comparator.comparingDouble(LexicalIndex.Match<RoutingProfile.Bound>::score).reversed().thenComparing(match -> match.value().moduleId().toString()))
                .limit(5).toList(), ranking.hasTopic(), ranking.writeRequest());
    }
}
