package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record FormatExpression(String format, List<InformationExpression> arguments) implements InformationExpression {
    public static final MapCodec<FormatExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("format").forGetter(FormatExpression::format),
            InformationExpression.CODEC.listOf().fieldOf("arguments").forGetter(FormatExpression::arguments)
    ).apply(i, FormatExpression::new));

    public FormatExpression {
        arguments = List.copyOf(arguments);
    }

    @Override
    public MapCodec<FormatExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        Object[] values = this.arguments.stream().map(value -> {
            Object argument = context.evaluate(value).argument();
            return argument instanceof Component text ? text.getString() : argument;
        }).toArray();
        try {
            return context.data(context.ops().createString(String.format(Locale.ROOT, this.format, values)));
        } catch (IllegalFormatException failure) {
            return new ExpressionValue.Missing(MissingReason.INVALID);
        }
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return collectGetters(this.arguments);
    }
}
