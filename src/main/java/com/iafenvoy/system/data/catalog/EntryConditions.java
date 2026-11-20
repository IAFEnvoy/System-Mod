package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Conditions govern acquiring new entitlements, never reclaiming an already earned reward.
 */
public record EntryConditions(EntityCondition displayCondition, EntityCondition condition, ActivityWindow activity) {
    public static final EntryConditions DEFAULT = new EntryConditions(AlwaysCondition.INSTANCE, AlwaysCondition.INSTANCE, ActivityWindow.ALWAYS);
    public static final MapCodec<EntryConditions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityCondition.optionalCodec("display_condition").forGetter(EntryConditions::displayCondition),
            EntityCondition.optionalCodec("condition").forGetter(EntryConditions::condition),
            ActivityWindow.CODEC.optionalFieldOf("activity", ActivityWindow.ALWAYS).forGetter(EntryConditions::activity)
    ).apply(i, EntryConditions::new));

    public EntryConditions(EntityCondition displayCondition, EntityCondition condition) {
        this(displayCondition, condition, ActivityWindow.ALWAYS);
    }
}
