package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record CompareExpression(Operation operation, InformationExpression left,
                                InformationExpression right) implements InformationExpression {
    public static final MapCodec<CompareExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Operation.CODEC.fieldOf("operation").forGetter(CompareExpression::operation),
            InformationExpression.CODEC.fieldOf("left").forGetter(CompareExpression::left),
            InformationExpression.CODEC.fieldOf("right").forGetter(CompareExpression::right)
    ).apply(i, CompareExpression::new));

    @Override
    public MapCodec<CompareExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        ExpressionValue left = context.evaluate(this.left), right = context.evaluate(this.right);
        if (left instanceof ExpressionValue.Missing missing) return missing;
        if (right instanceof ExpressionValue.Missing missing) return missing;
        boolean result;
        if (this.operation == Operation.EQ || this.operation == Operation.NE) {
            result = Objects.equals(left.argument(), right.argument());
            if (this.operation == Operation.NE) result = !result;
        } else {
            double a = left.number(), b = right.number();
            result = switch (this.operation) {
                case LT -> a < b;
                case LE -> a <= b;
                case GT -> a > b;
                case GE -> a >= b;
                case EQ, NE -> throw new IllegalStateException("Equality handled above");
            };
        }
        return context.data(context.ops().createBoolean(result));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.left, this.right);
    }

    public enum Operation {
        EQ, NE, LT, LE, GT, GE;
        public static final Codec<Operation> CODEC = ExtraEnumCodecs.enumCodec(Operation.class);
    }
}
