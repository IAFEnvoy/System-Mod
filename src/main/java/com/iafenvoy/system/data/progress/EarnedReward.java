package com.iafenvoy.system.data.progress;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * An earned snapshot survives removed or edited definitions. Transferred means credited to System Space.
 */
public record EarnedReward(String id, String category, PersistHolder<Reward> reward, long created,
                           boolean transferred) {
    public static final Codec<EarnedReward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(EarnedReward::id),
            Codec.STRING.fieldOf("category").forGetter(EarnedReward::category),
            PersistHolder.codec(Reward.SNAPSHOT_CODEC).fieldOf("reward").forGetter(EarnedReward::reward),
            Codec.LONG.fieldOf("created").forGetter(EarnedReward::created),
            Codec.BOOL.optionalFieldOf("transferred", false).forGetter(EarnedReward::transferred)
    ).apply(i, EarnedReward::new));

    public EarnedReward transfer() {
        return new EarnedReward(this.id, this.category, this.reward, this.created, true);
    }
}
