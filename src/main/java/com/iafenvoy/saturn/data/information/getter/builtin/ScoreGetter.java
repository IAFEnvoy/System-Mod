package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.api.playerview.PlayerView;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.data.information.model.ViewValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ScoreGetter(String objective) implements EntityValueGetter<Integer> {
    public static final MapCodec<ScoreGetter> CODEC = Codec.STRING.fieldOf("objective").xmap(ScoreGetter::new, ScoreGetter::objective);

    @Override
    public MapCodec<ScoreGetter> codec() {
        return CODEC;
    }

    @Override
    public Codec<Integer> valueCodec() {
        return Codec.INT;
    }

    @Override
    public ViewValue<Integer> capture(Entity entity) {
        return this.select(ScoresGetter.INSTANCE.capture(entity));
    }

    @Override
    public ViewValue<Integer> fallback(PlayerView view) {
        return this.select(view.read(ScoresGetter.INSTANCE));
    }

    @Override
    public List<EntityValueGetter<?>> dependencies() {
        return List.of(ScoresGetter.INSTANCE);
    }

    private ViewValue<Integer> select(ViewValue<Map<String, Integer>> scores) {
        if (!scores.isKnown()) return ViewValue.missing(scores.missingReason().orElseThrow());
        return Optional.ofNullable(scores.value().orElseThrow().get(this.objective)).map(value -> ViewValue.known(value, scores.origin()))
                .orElseGet(() -> ViewValue.missing(MissingReason.NOT_APPLICABLE));
    }
}
