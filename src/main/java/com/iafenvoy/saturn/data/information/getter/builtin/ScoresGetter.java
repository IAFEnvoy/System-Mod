package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.ValueOrigin;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ScoresGetter implements EntityValueGetter<Map<String, Integer>> {
    INSTANCE;

    public static final MapCodec<ScoresGetter> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<ScoresGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Map<String, Integer>> valueCodec() {
        return Codec.unboundedMap(Codec.STRING, Codec.INT);
    }

    @Override
    public ViewValue<Map<String, Integer>> capture(Entity entity) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        entity.level().getScoreboard().listPlayerScores(entity).forEach((objective, score) -> scores.put(objective.getName(), score));
        return ViewValue.known(scores, ValueOrigin.CAPTURED);
    }
}
