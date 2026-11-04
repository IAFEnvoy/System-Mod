package com.iafenvoy.system.agent.store;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, bounded projections; history never grants permissions or invents facts.
 */
public final class ConversationMemory {
    private ConversationMemory() {
    }

    public record Entry(String source, String text, String status, List<CardFact> facts) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("source").forGetter(Entry::source),
                Codec.STRING.optionalFieldOf("text", "").forGetter(Entry::text),
                Codec.STRING.optionalFieldOf("status", "").forGetter(Entry::status),
                CardFact.CODEC.listOf().optionalFieldOf("facts", List.of()).forGetter(Entry::facts)
        ).apply(i, Entry::new));
    }

    public static List<Entry> recent(PlayerData data, String currentRequest, int byteBudget) {
        List<Entry> selected = new ArrayList<>();
        int bytes = 2;
        for (int i = data.conversation.history.size() - 1; i >= 0 && selected.size() < 6; i--) {
            Message message = data.conversation.history.get(i);
            if (message.requestId().equals(currentRequest)) continue;
            Entry entry = switch (message) {
                case Message.User user -> new Entry("USER_STATEMENT", user.text(), "", List.of());
                case Message.Assistant assistant -> assistant(assistant.result(), byteBudget);
            };
            int length = size(entry) + 1;
            if (bytes + length > byteBudget) continue;
            bytes += length;
            selected.add(entry);
        }
        Collections.reverse(selected);
        return List.copyOf(selected);
    }

    private static Entry assistant(AgentResult result, int byteBudget) {
        List<CardFact> facts = new ArrayList<>(result.cards().stream().limit(2).map(ConversationMemory::fields).filter(Objects::nonNull).toList());
        Entry entry = new Entry("HISTORICAL_RESULT_REQUIRES_REVALIDATION", "", result.messageKey(), List.copyOf(facts));
        while (!facts.isEmpty() && size(entry) > byteBudget * 3 / 4) {
            facts.removeLast();
            entry = new Entry(entry.source(), "", entry.status(), List.copyOf(facts));
        }
        return entry;
    }

    private static int size(Entry entry) {
        return CodecIO.write(Entry.CODEC, entry).getBytes(StandardCharsets.UTF_8).length;
    }

    private static CardFact fields(CardUpdate card) {
        return card.data().flatMap(CardData::memoryFact).orElse(null);
    }
}
