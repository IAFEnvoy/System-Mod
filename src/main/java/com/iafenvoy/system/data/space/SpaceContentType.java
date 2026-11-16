package com.iafenvoy.system.data.space;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.assistant.space.SpaceTransfer;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;

/**
 * Metadata is pure and readable on either side. Preparation runs on the game thread and only stages effects.
 */
public interface SpaceContentType<T extends SpaceContent> {
    @SuppressWarnings("unchecked")
    static <T extends SpaceContent> SpaceContentType<T> of(T content) {
        return (SpaceContentType<T>) content.type();
    }

    MapCodec<T> codec();

    Component name(T content);

    IconReference icon(T content);

    String searchId(T content);

    int batchSize(T content);

    void prepare(T content, long amount, SpaceTransfer transfer);
}
