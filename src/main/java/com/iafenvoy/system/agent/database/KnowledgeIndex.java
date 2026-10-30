package com.iafenvoy.system.agent.database;

import com.iafenvoy.system.agent.routing.LexicalIndex;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.IOException;
import java.util.*;

/**
 * Immutable passage vectors, independent of the database scope or source module.
 * Callers supply authorized IDs and recheck native permissions before returning hits.
 */
public final class KnowledgeIndex {
    private static final int PASSAGE_LENGTH = 480;
    private final LexicalIndex<Passage> index;

    public record Document(String id, String title, String chapter, String body) {
        public static final Codec<Document> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Document::id),
                Codec.STRING.fieldOf("title").forGetter(Document::title),
                Codec.STRING.fieldOf("chapter").forGetter(Document::chapter),
                Codec.STRING.fieldOf("body").forGetter(Document::body)
        ).apply(i, Document::new));
    }

    public record Hit(Document document, String passage, double score) {
    }

    private record Passage(Document document, String text) {
    }

    KnowledgeIndex(List<Document> documents, String language) {
        List<LexicalIndex.Example<Passage>> examples = new ArrayList<>();
        for (Document document : documents) {
            List<String> passages = passages(document.body());
            for (String text : passages)
                examples.add(new LexicalIndex.Example<>(new Passage(document, text), language,
                        document.title() + "\n" + document.chapter() + "\n" + text));
            if (!passages.isEmpty())
                examples.add(new LexicalIndex.Example<>(new Passage(document, passages.getFirst()), language, document.title()));
        }
        try {
            this.index = new LexicalIndex<>(examples, false);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to build knowledge index", error);
        }
    }

    List<Hit> search(Set<String> allowed, String query, String language, int limit) {
        if (limit <= 0) return List.of();
        // Scores measure lexical relevance, not calibrated probabilities; weak overlaps produce no answer.
        Map<String, Hit> hits = new LinkedHashMap<>();
        for (LexicalIndex.Match<Passage> match : this.index.search(query, language).matches()) {
            Passage passage = match.value();
            if (!allowed.contains(passage.document().id()) || match.score() < 0.08 || match.coverage() < 0.25) continue;
            hits.putIfAbsent(passage.document().id(), new Hit(passage.document(), passage.text(), match.score()));
            if (hits.size() == limit) break;
        }
        return List.copyOf(hits.values());
    }

    private static List<String> passages(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\\R\\s*\\R|(?<=[。！？])")) {
            if (paragraph.isBlank()) continue;
            if (!current.isEmpty() && current.length() + paragraph.length() > PASSAGE_LENGTH) {
                result.add(current.toString().strip());
                current.setLength(0);
            }
            int offset = 0;
            while (paragraph.length() - offset > PASSAGE_LENGTH) {
                int end = offset + PASSAGE_LENGTH;
                if (Character.isHighSurrogate(paragraph.charAt(end - 1))) end--;
                result.add(paragraph.substring(offset, end).strip());
                offset = end;
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(paragraph.substring(offset));
        }
        if (!current.isEmpty()) result.add(current.toString().strip());
        return List.copyOf(result);
    }
}
