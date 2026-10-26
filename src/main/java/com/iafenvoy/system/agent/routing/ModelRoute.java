package com.iafenvoy.system.agent.routing;

import com.google.gson.JsonElement;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.store.ConversationMemory;
import com.iafenvoy.system.util.AgentCodecs;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/** The model selects the same registered input type used by buttons and exact commands. */
public record ModelRoute(TypedPayload<?> call) {
    public static Codec<ModelRoute> codec(Function<Identifier, TypedPayload.Type<?>> resolver) {
        Codec<String> ids = Codec.STRING.validate(id -> {
            Identifier parsed = Identifier.tryParse(id);
            return id.equals("clarify") || parsed != null && resolver.apply(parsed) != null
                    ? DataResult.success(id) : DataResult.error(() -> "Unavailable model capability " + id);
        });
        return ids.dispatch("capabilityId",
            value -> value.call == null ? "clarify" : value.call.type().id().toString(),
            id -> id.equals("clarify")
                    ? AgentCodecs.strict(EmptyInput.CODEC.codec().fieldOf("input").xmap(_ -> new ModelRoute(null), _ -> EmptyInput.INSTANCE), "capabilityId")
                    : AgentCodecs.strict(ResultTypes.ACTIONS.valueCodec(Identifier.parse(id), "input").xmap(ModelRoute::new, ModelRoute::call), "capabilityId"));
    }

    public record Capability(String description, JsonElement input) {
        public static final Codec<Capability> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("description").forGetter(Capability::description),
                CodecIO.JSON.fieldOf("input").forGetter(Capability::input)
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
