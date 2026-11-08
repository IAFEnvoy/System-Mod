package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.mojang.serialization.MapCodec;

import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record IsKnownExpression(InformationExpression value) implements InformationExpression {
    public static final MapCodec<IsKnownExpression> CODEC = InformationExpression.CODEC.fieldOf("value").xmap(IsKnownExpression::new, IsKnownExpression::value);

    @Override
    public MapCodec<IsKnownExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        ExpressionValue value = context.evaluate(this.value);
        boolean known = !(value instanceof ExpressionValue.Missing) && !(value instanceof ExpressionValue.Display(
                InformationValue value1
        ) && value1 instanceof InformationValue.Missing);
        return context.data(context.ops().createBoolean(known));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.value);
    }
}
