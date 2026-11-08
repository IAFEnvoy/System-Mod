package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.mojang.serialization.MapCodec;

import java.util.List;
import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record CoalesceExpression(List<InformationExpression> values) implements InformationExpression {
    public static final MapCodec<CoalesceExpression> CODEC = InformationExpression.CODEC.listOf().fieldOf("values").xmap(CoalesceExpression::new, CoalesceExpression::values);

    public CoalesceExpression {
        values = List.copyOf(values);
    }

    @Override
    public MapCodec<CoalesceExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        ExpressionValue first = new ExpressionValue.Missing(MissingReason.NOT_CAPTURED);
        for (int i = 0; i < this.values.size(); i++) {
            ExpressionValue value = context.evaluate(this.values.get(i));
            if (i == 0) first = value;
            if (!(value instanceof ExpressionValue.Missing) && !(value instanceof ExpressionValue.Display(
                    InformationValue value1
            ) && value1 instanceof InformationValue.Missing)) return value;
        }
        return first;
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.values);
    }
}
