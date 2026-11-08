package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Set;

/**
 * Embeds a configured getter. Path is only for selecting members of a structured result.
 */
public record GetterExpression(EntityValueGetter<?> getter, List<String> path) implements InformationExpression {
    public static final MapCodec<GetterExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityValueGetter.CODEC.fieldOf("getter").forGetter(GetterExpression::getter),
            Codec.STRING.listOf().optionalFieldOf("path", List.of()).forGetter(GetterExpression::path)
    ).apply(i, GetterExpression::new));

    public GetterExpression {
        path = List.copyOf(path);
    }

    @Override
    public MapCodec<GetterExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        return context.getter(this.getter, this.path);
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return Set.of(this.getter);
    }
}
