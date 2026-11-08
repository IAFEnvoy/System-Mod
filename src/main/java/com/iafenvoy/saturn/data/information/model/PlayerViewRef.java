package com.iafenvoy.saturn.data.information.model;

import com.iafenvoy.saturn.Saturn;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record PlayerViewRef(UUID accountId, ResourceLocation source, String recordId) {
    public static final Codec<PlayerViewRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("account").forGetter(PlayerViewRef::accountId),
            ResourceLocation.CODEC.fieldOf("source").forGetter(PlayerViewRef::source),
            Codec.string(1, 256).fieldOf("record").forGetter(PlayerViewRef::recordId)
    ).apply(i, PlayerViewRef::new));

    public PlayerViewRef {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(source);
        if (recordId == null || recordId.isBlank() || recordId.length() > 256)
            throw new IllegalArgumentException("Invalid player view record id");
    }

    public static PlayerViewRef current(UUID account) {
        return new PlayerViewRef(account, ResourceLocation.fromNamespaceAndPath(Saturn.MOD_ID, "current"), "current");
    }
}
