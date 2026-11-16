package com.iafenvoy.system.data.space;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;

/**
 * Opaque stored content; new kinds do not change the space ledger or withdrawal workflow.
 */
public interface SpaceContent {
    Codec<SpaceContent> CODEC = Codec.lazyInitialized(() -> SystemRegistries.SPACE_CONTENT_TYPE.byNameCodec()
            .dispatch("type", SpaceContent::type, SpaceContentType::codec));

    SpaceContentType<?> type();
}
