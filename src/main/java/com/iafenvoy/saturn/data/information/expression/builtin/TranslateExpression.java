package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.iafenvoy.saturn.data.information.expression.builtin.ExpressionSupport.collectGetters;

public record TranslateExpression(InformationExpression key, List<InformationExpression> arguments,
                                  Optional<InformationExpression> fallback) implements InformationExpression {
    public static final MapCodec<TranslateExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            InformationExpression.CODEC.fieldOf("key").forGetter(TranslateExpression::key),
            InformationExpression.CODEC.listOf().optionalFieldOf("arguments", List.of()).forGetter(TranslateExpression::arguments),
            InformationExpression.CODEC.optionalFieldOf("fallback").forGetter(TranslateExpression::fallback)
    ).apply(i, TranslateExpression::new));

    public TranslateExpression {
        arguments = List.copyOf(arguments);
    }

    @Override
    public MapCodec<TranslateExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        String key = context.evaluate(this.key).decode(Codec.STRING);
        Object[] arguments = this.arguments.stream().map(value -> {
            Object argument = context.evaluate(value).argument();
            if (argument instanceof Double number && number == Math.rint(number) && Math.abs(number) < 1.0E15)
                return (Object) number.longValue();
            return argument;
        }).toArray();
        String fallback = this.fallback.map(value -> context.evaluate(value).decode(Codec.STRING)).orElse(null);
        return new ExpressionValue.Display(new InformationValue.Text(Component.translatableWithFallback(key, fallback, arguments)));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        List<InformationExpression> all = new ArrayList<>(this.arguments);
        all.add(this.key);
        this.fallback.ifPresent(all::add);
        return collectGetters(all);
    }
}
