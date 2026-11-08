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

public record LocalExpression(String name, List<String> path) implements InformationExpression {
    public static final MapCodec<LocalExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "item").forGetter(LocalExpression::name),
            Codec.STRING.listOf().optionalFieldOf("path", List.of()).forGetter(LocalExpression::path)
    ).apply(i, LocalExpression::new));

    public LocalExpression {
        path = List.copyOf(path);
    }

    @Override
    public MapCodec<LocalExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        return context.local(this.name, this.path);
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return Set.of();
    }
}
