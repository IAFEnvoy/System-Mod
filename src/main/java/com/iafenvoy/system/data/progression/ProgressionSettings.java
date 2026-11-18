package com.iafenvoy.system.data.progression;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Server-owned percentile and reward window; no item maximum is used as a progression signal.
 */
public record ProgressionSettings(double percentile, double lowerFactor, double upperFactor, boolean recipes) {
    public static final ProgressionSettings DEFAULT = new ProgressionSettings(0.9, 0.8, 1.3, true);
    public static final Codec<ProgressionSettings> CODEC = RecordCodecBuilder.<ProgressionSettings>create(i -> i.group(
            Codec.doubleRange(0.5, 0.99).optionalFieldOf("percentile", DEFAULT.percentile).forGetter(ProgressionSettings::percentile),
            MiscCodecs.POSITIVE_DOUBLE.optionalFieldOf("lower_factor", DEFAULT.lowerFactor).forGetter(ProgressionSettings::lowerFactor),
            MiscCodecs.POSITIVE_DOUBLE.optionalFieldOf("upper_factor", DEFAULT.upperFactor).forGetter(ProgressionSettings::upperFactor),
            Codec.BOOL.optionalFieldOf("recipes", DEFAULT.recipes).forGetter(ProgressionSettings::recipes)
    ).apply(i, ProgressionSettings::new)).validate(settings -> settings.lowerFactor > settings.upperFactor
            ? DataResult.error(() -> "Progression lower_factor must not exceed upper_factor") : DataResult.success(settings));
}
