package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.mojang.serialization.MapCodec;

import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record BooleanExpression(InformationExpression value) implements InformationExpression {
    public static final MapCodec<BooleanExpression> CODEC = InformationExpression.CODEC.fieldOf("value").xmap(BooleanExpression::new, BooleanExpression::value);

    @Override
    public MapCodec<BooleanExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        return new ExpressionValue.Display(new InformationValue.Flag(context.evaluate(this.value).flag()));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.value);
    }
}
