package com.iafenvoy.saturn.data.information.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record PlayerViewRequest(long requestId, PlayerViewRef ref, List<ResourceLocation> fields) {
    public static final Codec<PlayerViewRequest> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("request").forGetter(PlayerViewRequest::requestId),
            PlayerViewRef.CODEC.fieldOf("ref").forGetter(PlayerViewRequest::ref),
            ResourceLocation.CODEC.listOf().fieldOf("fields").forGetter(PlayerViewRequest::fields)
    ).apply(i, PlayerViewRequest::new));

    public PlayerViewRequest {
        Objects.requireNonNull(ref);
        fields = List.copyOf(fields);
        if (fields.size() > 256) throw new IllegalArgumentException("Too many requested player view fields");
    }
}
