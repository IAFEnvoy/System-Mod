package com.iafenvoy.saturn.data.information.expression;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.iafenvoy.saturn.data.information.model.MissingReason;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Intermediate data is kept separate from the final presentation model.
 */
public sealed interface ExpressionValue {
    record Data(Dynamic<JsonElement> data) implements ExpressionValue {
    }

    record Display(InformationValue value) implements ExpressionValue {
    }

    record Missing(MissingReason reason) implements ExpressionValue {
    }

    default <T> T decode(Codec<T> codec) {
        if (this instanceof Missing(MissingReason reason1)) throw new Unavailable(reason1);
        if (this instanceof Display(InformationValue value) && value instanceof InformationValue.Missing(
                MissingReason reason
        )) throw new Unavailable(reason);
        if (!(this instanceof Data(Dynamic<JsonElement> data1))) throw new Unavailable(MissingReason.INVALID);
        return codec.parse(data1.getOps(), data1.getValue()).result().orElseThrow(() -> new Unavailable(MissingReason.INVALID));
    }

    default double number() {
        if (this instanceof Display(InformationValue value1) && value1 instanceof InformationValue.Number(
                double value
        )) return value;
        return this.decode(MiscCodecs.FINITE);
    }

    default boolean flag() {
        if (this instanceof Display(InformationValue value1) && value1 instanceof InformationValue.Flag(
                boolean value
        )) return value;
        return this.decode(Codec.BOOL);
    }

    default Component text() {
        if (this instanceof Display(InformationValue value) && value instanceof InformationValue.Text text)
            return text.text();
        if (this instanceof Data(Dynamic<JsonElement> data1) && data1.getValue().isJsonPrimitive()) {
            JsonPrimitive primitive = data1.getValue().getAsJsonPrimitive();
            if (primitive.isString() || primitive.isNumber() || primitive.isBoolean())
                return Component.literal(primitive.getAsString());
        }
        return this.decode(ComponentSerialization.CODEC);
    }

    default Object argument() {
        if (this instanceof Data(Dynamic<JsonElement> data1) && data1.getValue().isJsonPrimitive()) {
            JsonPrimitive primitive = data1.getValue().getAsJsonPrimitive();
            if (primitive.isNumber()) return this.number();
            if (primitive.isBoolean()) return this.flag();
            if (primitive.isString()) return primitive.getAsString();
        }
        if (this instanceof Display(InformationValue value3) && value3 instanceof InformationValue.Number(
                double value2
        )) return value2;
        if (this instanceof Display(InformationValue value1) && value1 instanceof InformationValue.Flag(
                boolean value
        )) return value;
        return this.text();
    }

    default InformationValue information() {
        switch (this) {
            case Missing(MissingReason reason) -> {
                return new InformationValue.Missing(reason);
            }
            case Display(InformationValue value) -> {
                return value;
            }
            case Data(Dynamic<JsonElement> data1) when data1.getValue().isJsonPrimitive() -> {
                JsonPrimitive primitive = data1.getValue().getAsJsonPrimitive();
                try {
                    if (primitive.isNumber()) return new InformationValue.Number(this.number());
                    if (primitive.isBoolean()) return new InformationValue.Flag(this.flag());
                    if (primitive.isString()) return new InformationValue.Text(this.text());
                } catch (Unavailable failure) {
                    return new InformationValue.Missing(failure.reason);
                }
            }
            default -> {
            }
        }
        return new InformationValue.Missing(MissingReason.INVALID);
    }

    /**
     * Internal control flow for strict operand conversion; zero, false and empty collections remain known.
     */
    final class Unavailable extends RuntimeException {
        public final MissingReason reason;

        public Unavailable(MissingReason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }
    }
}
