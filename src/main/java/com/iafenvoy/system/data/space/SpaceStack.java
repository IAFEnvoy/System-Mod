package com.iafenvoy.system.data.space;

import com.iafenvoy.saturn.data.IconReference;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;

import java.util.Objects;

/**
 * A typed grant without a storage entry ID, claim state or provenance.
 */
public record SpaceStack(SpaceContent content, long amount) {
    public static final Codec<SpaceStack> CODEC = RecordCodecBuilder.create(i -> i.group(
            SpaceContent.CODEC.fieldOf("content").forGetter(SpaceStack::content),
            ExtraCodecs.POSITIVE_LONG.optionalFieldOf("amount", 1L).forGetter(SpaceStack::amount)
    ).apply(i, SpaceStack::new));

    public SpaceStack {
        Objects.requireNonNull(content, "content");
        if (amount <= 0) throw new IllegalArgumentException("Space amount must be positive");
    }

    public Component name() {
        return SpaceContentType.of(this.content).name(this.content);
    }

    public IconReference icon() {
        return SpaceContentType.of(this.content).icon(this.content);
    }

    public Component label() {
        return Component.translatable("system.ui.item_amount", this.name(), Long.toString(this.amount));
    }
}
