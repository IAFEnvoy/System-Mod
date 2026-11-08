package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record MeterExpression(InformationExpression value, InformationExpression min,
                              InformationExpression max) implements InformationExpression {
    public static final MapCodec<MeterExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            InformationExpression.CODEC.fieldOf("value").forGetter(MeterExpression::value),
            InformationExpression.CODEC.optionalFieldOf("min", new ConstantExpression(new Dynamic<>(JsonOps.INSTANCE, JsonOps.INSTANCE.createInt(0)))).forGetter(MeterExpression::min),
            InformationExpression.CODEC.fieldOf("max").forGetter(MeterExpression::max)
    ).apply(i, MeterExpression::new));

    @Override
    public MapCodec<MeterExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        double value = context.evaluate(this.value).number(), min = context.evaluate(this.min).number(), max = context.evaluate(this.max).number();
        if (max < min) return new ExpressionValue.Missing(MissingReason.INVALID);
        return new ExpressionValue.Display(new InformationValue.Meter(value, min, max));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.value, this.min, this.max);
    }
}
