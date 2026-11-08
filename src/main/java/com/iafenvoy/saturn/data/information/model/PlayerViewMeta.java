package com.iafenvoy.saturn.data.information.model;

import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlayerViewMeta(PlayerViewRef ref, UUID snapshotId, SourceKind sourceKind,
                             Optional<Long> capturedAt, Optional<Long> capturedGameTime, long assembledAt) {
    public static final Codec<PlayerViewMeta> CODEC = RecordCodecBuilder.create(i -> i.group(
            PlayerViewRef.CODEC.fieldOf("ref").forGetter(PlayerViewMeta::ref),
            UUIDUtil.CODEC.fieldOf("snapshot").forGetter(PlayerViewMeta::snapshotId),
            SourceKind.CODEC.fieldOf("kind").forGetter(PlayerViewMeta::sourceKind),
            Codec.LONG.optionalFieldOf("captured_at").forGetter(PlayerViewMeta::capturedAt),
            Codec.LONG.optionalFieldOf("game_time").forGetter(PlayerViewMeta::capturedGameTime),
            Codec.LONG.fieldOf("assembled_at").forGetter(PlayerViewMeta::assembledAt)
    ).apply(i, PlayerViewMeta::new));

    public PlayerViewMeta {
        Objects.requireNonNull(ref);
        Objects.requireNonNull(snapshotId);
        Objects.requireNonNull(sourceKind);
        Objects.requireNonNull(capturedAt);
        Objects.requireNonNull(capturedGameTime);
    }

    public enum SourceKind {
        LIVE, SAVED, ARCHIVE, SUMMARY;

        public static final Codec<SourceKind> CODEC = ExtraEnumCodecs.enumCodec(SourceKind.class);
    }
}
