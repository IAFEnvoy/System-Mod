package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.codec.CollectionCodecs;
import com.iafenvoy.system.agent.AgentResult;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disposable memory and replay caches share one lifetime; inline codecs keep their stored field names.
 */
public final class Conversation {
    public static final MapCodec<Conversation> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.optionalFieldOf("memoryEpoch", 0L).forGetter(v -> v.memoryEpoch),
            // Tolerant per entry: one unreadable turn never discards the whole conversation.
            CollectionCodecs.list(Message.CODEC).lenientOptionalFieldOf("history", List.of()).forGetter(v -> v.history),
            CollectionCodecs.map(Codec.STRING, AgentResult.CODEC).lenientOptionalFieldOf("requests", Map.of()).forGetter(v -> v.requests),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("requestInputs", Map.of()).forGetter(v -> v.requestInputs)
    ).apply(i, Conversation::new));
    public long memoryEpoch;
    public final List<Message> history;
    public final Map<String, AgentResult> requests;
    public final Map<String, String> requestInputs;

    public Conversation() {
        this(0, List.of(), Map.of(), Map.of());
    }

    private Conversation(long memoryEpoch, List<Message> history, Map<String, AgentResult> requests, Map<String, String> requestInputs) {
        this.memoryEpoch = memoryEpoch;
        // Codec defaults may be immutable; each player owns fresh mutable collections.
        this.history = new ArrayList<>(history);
        this.requests = new LinkedHashMap<>(requests);
        this.requestInputs = new LinkedHashMap<>(requestInputs);
    }

    public Conversation copy() {
        return new Conversation(this.memoryEpoch, this.history, this.requests, this.requestInputs);
    }

    public void clear() {
        this.memoryEpoch++;
        this.history.clear();
        this.requests.clear();
        this.requestInputs.clear();
    }
}
