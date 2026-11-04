package com.iafenvoy.system.agent.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Daily admission counts only; cumulative billed usage remains in world SavedData.
 */
public final class ModelQuota {
    public static final MapCodec<ModelQuota> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("usageDate", "").forGetter(v -> v.usageDate),
            Codec.INT.optionalFieldOf("usedModelCalls", 0).forGetter(v -> v.usedModelCalls)
    ).apply(i, ModelQuota::new));
    public String usageDate;
    public int usedModelCalls;

    public ModelQuota() {
        this("", 0);
    }

    private ModelQuota(String usageDate, int usedModelCalls) {
        this.usageDate = usageDate;
        this.usedModelCalls = usedModelCalls;
    }

    public ModelQuota copy() {
        return new ModelQuota(this.usageDate, this.usedModelCalls);
    }
}
