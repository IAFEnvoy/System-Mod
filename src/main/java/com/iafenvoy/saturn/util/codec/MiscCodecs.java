package com.iafenvoy.saturn.util.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MiscCodecs {
    public static final Codec<String> NOT_BLANK_STRING = checked(Codec.STRING, value -> !value.isBlank());
    public static final Codec<Double> FINITE = Codec.DOUBLE.validate(value -> Double.isFinite(value) ? DataResult.success(value) : DataResult.error(() -> "Number value must be finite: " + value));

    // A bare JSON string is a translation key and an object is a full component, so a definition stays readable
    // without losing styling.
    public static final Codec<Component> TRANSLATABLE_COMPONENT = Codec.either(Codec.STRING, ComponentSerialization.CODEC).xmap(e -> e.map(Component::translatable, Function.identity()), Either::right);

    /**
     * Two fields read as one group: {@code RecordCodecBuilder.group} stops at sixteen components, so a definition
     * with more fields pairs two of them and stays under the limit. The JSON keys are unchanged.
     */
    public static <A, B> MapCodec<Pair<A, B>> pair(MapCodec<A> first, MapCodec<B> second) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                first.forGetter(Pair::getFirst),
                second.forGetter(Pair::getSecond)
        ).apply(i, Pair::of));
    }

    public static <T> Codec<List<T>> combineCodec(Codec<T> codec) {
        return Codec.either(codec, TolerantListCodec.create(codec)).xmap(x -> x.map(List::of, l -> l), l -> l.size() == 1 ? Either.left(l.getFirst()) : Either.right(l));
    }

    public static DataResult<Integer> parseInt(String value) {
        try {
            return DataResult.success(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "Invalid integer map key: " + value);
        }
    }

    public static <T> DataResult<T> mapOrError(Supplier<T> supplier) {
        try {
            return DataResult.success(supplier.get());
        } catch (Exception e) {
            return DataResult.error(() -> "Error mapping object: " + e.getMessage());
        }
    }

    public static Codec<String> string(String regex) {
        return checked(Codec.STRING, value -> value.matches(regex));
    }

    public static <A> Codec<A> checked(Codec<A> codec, Predicate<A> validator) {
        return codec.validate(x -> validator.test(x) ? DataResult.success(x) : DataResult.error(() -> "Invalid configuration value"));
    }
}
