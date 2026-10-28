package com.iafenvoy.system.agent.routing;

import com.google.gson.JsonElement;
import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.store.ConversationMemory;
import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The model selects the same registered input type used by buttons and exact commands.
 */
public record ModelRoute(AgentAction call) {
    public static Codec<ModelRoute> codec(Collection<MapCodec<? extends AgentAction>> capabilities) {
        Map<String, MapCodec<? extends ModelRoute>> types = new LinkedHashMap<>();
        types.put("clarify", EmptyInput.CODEC.codec().fieldOf("input")
                .xmap(_ -> new ModelRoute(null), _ -> EmptyInput.INSTANCE));
        for (MapCodec<? extends AgentAction> codec : capabilities)
            types.put(SystemRegistries.ACTION_TYPE.getKey(codec).toString(), choice(codec));
        Codec<String> ids = Codec.STRING.validate(id -> types.containsKey(id)
                ? DataResult.success(id) : DataResult.error(() -> "Unavailable model capability " + id));
        return ids.dispatch("capabilityId",
                value -> value.call == null ? "clarify" : value.call.id().toString(),
                types::get);
    }

    private static <I extends AgentAction> MapCodec<ModelRoute> choice(MapCodec<I> codec) {
        return codec.codec().fieldOf("input").xmap(ModelRoute::new, route -> {
            if (route.call == null || route.call.codec() != codec)
                throw new IllegalArgumentException("Incorrect model action codec");
            @SuppressWarnings("unchecked") I input = (I) route.call;
            return input;
        });
    }

    public record Capability(String description, JsonElement input) {
        public static final Codec<Capability> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("description").forGetter(Capability::description),
                ExtraCodecs.JSON.fieldOf("input").forGetter(Capability::input)
        ).apply(i, Capability::new));
        public static final Codec<Map<String, Capability>> CATALOGUE = Codec.unboundedMap(Codec.STRING, CODEC);
    }

    public record Candidate(String moduleId, double similarity) {
        public static final Codec<Candidate> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("moduleId").forGetter(Candidate::moduleId),
                Codec.DOUBLE.fieldOf("similarity").forGetter(Candidate::similarity)
        ).apply(i, Candidate::new));
    }

    public record Input(String currentRequest, List<ConversationMemory.Entry> history, List<Candidate> candidates) {
        public static final MapCodec<Input> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("currentRequest").forGetter(Input::currentRequest),
                ConversationMemory.Entry.CODEC.listOf().fieldOf("untrustedRecentHistory").forGetter(Input::history),
                Candidate.CODEC.listOf().fieldOf("untrustedSemanticCandidates").forGetter(Input::candidates)
        ).apply(i, Input::new));
    }
}
