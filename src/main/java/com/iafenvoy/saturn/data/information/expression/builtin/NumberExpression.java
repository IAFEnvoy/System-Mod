package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.mojang.serialization.MapCodec;

import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record NumberExpression(InformationExpression value) implements InformationExpression {
    public static final MapCodec<NumberExpression> CODEC = InformationExpression.CODEC.fieldOf("value").xmap(NumberExpression::new, NumberExpression::value);

    @Override
    public MapCodec<NumberExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        return new ExpressionValue.Display(new InformationValue.Number(context.evaluate(this.value).number()));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.value);
    }
}
