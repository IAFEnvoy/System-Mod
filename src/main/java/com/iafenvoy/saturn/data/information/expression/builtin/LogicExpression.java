package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record LogicExpression(Operation operation,
                              List<InformationExpression> values) implements InformationExpression {
    public static final MapCodec<LogicExpression> CODEC = RecordCodecBuilder.<LogicExpression>mapCodec(i -> i.group(
            Operation.CODEC.fieldOf("operation").forGetter(LogicExpression::operation),
            InformationExpression.CODEC.listOf().fieldOf("values").forGetter(LogicExpression::values)
    ).apply(i, LogicExpression::new)).validate(value -> value.operation == Operation.NOT && value.values.size() != 1
            ? DataResult.error(() -> "not requires exactly one operand") : DataResult.success(value));

    public LogicExpression {
        values = List.copyOf(values);
    }

    @Override
    public MapCodec<LogicExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        boolean result = switch (this.operation) {
            case AND -> this.values.stream().allMatch(value -> context.evaluate(value).flag());
            case OR -> this.values.stream().anyMatch(value -> context.evaluate(value).flag());
            case NOT -> !context.evaluate(this.values.getFirst()).flag();
        };
        return context.data(context.ops().createBoolean(result));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.values);
    }

    public enum Operation {
        AND, OR, NOT;
        public static final Codec<Operation> CODEC = ExtraEnumCodecs.enumCodec(Operation.class);
    }
}
