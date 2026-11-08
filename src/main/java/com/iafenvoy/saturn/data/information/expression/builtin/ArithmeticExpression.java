package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record ArithmeticExpression(Operation operation, InformationExpression left,
                                   InformationExpression right) implements InformationExpression {
    public static final MapCodec<ArithmeticExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Operation.CODEC.fieldOf("operation").forGetter(ArithmeticExpression::operation),
            InformationExpression.CODEC.fieldOf("left").forGetter(ArithmeticExpression::left),
            InformationExpression.CODEC.fieldOf("right").forGetter(ArithmeticExpression::right)
    ).apply(i, ArithmeticExpression::new));

    @Override
    public MapCodec<ArithmeticExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        double left = context.evaluate(this.left).number(), right = context.evaluate(this.right).number();
        double result = switch (this.operation) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> left / right;
            case MIN -> Math.min(left, right);
            case MAX -> Math.max(left, right);
        };
        return Double.isFinite(result) ? context.data(context.ops().createDouble(result)) : new ExpressionValue.Missing(MissingReason.INVALID);
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.left, this.right);
    }

    public enum Operation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE, MIN, MAX;
        public static final Codec<Operation> CODEC = ExtraEnumCodecs.enumCodec(Operation.class);
    }
}
