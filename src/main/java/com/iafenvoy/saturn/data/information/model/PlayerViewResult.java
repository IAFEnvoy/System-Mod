package com.iafenvoy.saturn.data.information.model;

import com.iafenvoy.saturn.data.information.PlayerViewSnapshot;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

public record PlayerViewResult(long requestId, Status status, Optional<PlayerViewSnapshot> snapshot) {
    public static final Codec<PlayerViewResult> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("request").forGetter(PlayerViewResult::requestId),
            Status.CODEC.fieldOf("status").forGetter(PlayerViewResult::status),
            PlayerViewSnapshot.CODEC.optionalFieldOf("snapshot").forGetter(PlayerViewResult::snapshot)
    ).apply(i, PlayerViewResult::new));

    public PlayerViewResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(snapshot);
        if ((status == Status.SUCCESS) != snapshot.isPresent())
            throw new IllegalArgumentException("Only a successful result has a snapshot");
    }

    public static PlayerViewResult success(long requestId, PlayerViewSnapshot snapshot) {
        return new PlayerViewResult(requestId, Status.SUCCESS, Optional.of(snapshot));
    }

    public static PlayerViewResult failure(long requestId, Status status) {
        return new PlayerViewResult(requestId, status, Optional.empty());
    }

    public enum Status {
        SUCCESS, DENIED, NOT_FOUND, CORRUPT, UNSUPPORTED, FAILED;

        public static final Codec<Status> CODEC = ExtraEnumCodecs.enumCodec(Status.class);
    }
}
