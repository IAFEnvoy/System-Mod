package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record ConditionalExpression(InformationExpression condition, InformationExpression thenValue,
                                    InformationExpression elseValue) implements InformationExpression {
    public static final MapCodec<ConditionalExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            InformationExpression.CODEC.fieldOf("condition").forGetter(ConditionalExpression::condition),
            InformationExpression.CODEC.fieldOf("then").forGetter(ConditionalExpression::thenValue),
            InformationExpression.CODEC.fieldOf("else").forGetter(ConditionalExpression::elseValue)
    ).apply(i, ConditionalExpression::new));

    @Override
    public MapCodec<ConditionalExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        return context.evaluate(context.evaluate(this.condition).flag() ? this.thenValue : this.elseValue);
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.condition, this.thenValue, this.elseValue);
    }
}
