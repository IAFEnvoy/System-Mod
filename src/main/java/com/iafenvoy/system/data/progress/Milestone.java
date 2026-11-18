package com.iafenvoy.system.data.progress;

import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * IDs identify entitlements, so changing a threshold never grants the same milestone twice.
 */
public record Milestone(Identifier id, int target, Reward reward) {
    public static final Codec<Milestone> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(Milestone::id),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("target").forGetter(Milestone::target),
            Reward.CODEC.codec().fieldOf("reward").forGetter(Milestone::reward)
    ).apply(i, Milestone::new));
}
