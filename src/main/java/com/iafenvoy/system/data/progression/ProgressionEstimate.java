package com.iafenvoy.system.data.progression;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Immutable estimate of observed progression, not proof that every unlocked recipe is craftable.
 */
public record ProgressionEstimate(double value, double lower, double upper, double minimum, int samples,
                                  int recipeSamples,
                                  double percentile, Confidence confidence) {
    public static final Codec<ProgressionEstimate> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("value").forGetter(ProgressionEstimate::value),
            MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("lower").forGetter(ProgressionEstimate::lower),
            MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("upper").forGetter(ProgressionEstimate::upper),
            MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("minimum").forGetter(ProgressionEstimate::minimum),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("samples").forGetter(ProgressionEstimate::samples),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("recipeSamples").forGetter(ProgressionEstimate::recipeSamples),
            Codec.doubleRange(0.5, 0.99).fieldOf("percentile").forGetter(ProgressionEstimate::percentile),
            Confidence.CODEC.fieldOf("confidence").forGetter(ProgressionEstimate::confidence)
    ).apply(i, ProgressionEstimate::new));

    public enum Confidence implements StringRepresentable {
        LOW, MEDIUM, HIGH;

        public static final Codec<Confidence> CODEC = StringRepresentable.fromEnum(Confidence::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
