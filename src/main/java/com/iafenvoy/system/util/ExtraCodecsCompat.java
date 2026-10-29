package com.iafenvoy.system.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FastColor;

import java.util.function.Function;

/**
 * The codec helpers Minecraft 26.1 adds to {@code ExtraCodecs} but 1.21.1 does not have.
 *
 * <p>Only the members this mod actually uses are ported, with the same validation messages, so a datapack that parses
 * on 26.1 keeps parsing here.
 */
public final class ExtraCodecsCompat {
    public static final Codec<Long> NON_NEGATIVE_LONG = longRangeWithMessage(0L, Long.MAX_VALUE, n -> "Value must be non-negative: " + n);
    public static final Codec<Long> POSITIVE_LONG = longRangeWithMessage(1L, Long.MAX_VALUE, n -> "Value must be positive: " + n);
    // 1.21.1 has no three-channel colour codec, so the 26.1 one is rebuilt from the same vector codec.
    public static final Codec<Integer> RGB_COLOR_CODEC = Codec.withAlternative(Codec.INT, ExtraCodecs.VECTOR3F,
            v -> FastColor.ARGB32.colorFromFloat(1.0F, v.x(), v.y(), v.z()));
    public static final Codec<Integer> STRING_RGB_COLOR = Codec.withAlternative(hexColor(6).xmap(ExtraCodecsCompat::opaque, ExtraCodecsCompat::transparent), RGB_COLOR_CODEC);
    public static final Codec<Integer> STRING_ARGB_COLOR = Codec.withAlternative(hexColor(8), ExtraCodecs.ARGB_COLOR_CODEC);

    private ExtraCodecsCompat() {
    }

    // Bit-for-bit ports of the 26.1 ARGB helpers that FastColor.ARGB32 does not carry in 1.21.1.
    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private static int transparent(int color) {
        return color & 0xFFFFFF;
    }

    public static Codec<Long> longRange(long minInclusive, long maxInclusive) {
        return longRangeWithMessage(minInclusive, maxInclusive, n -> "Value must be within range [" + minInclusive + ";" + maxInclusive + "]: " + n);
    }

    private static Codec<Long> longRangeWithMessage(long minInclusive, long maxInclusive, Function<Long, String> error) {
        return Codec.LONG.validate(value -> value >= minInclusive && value <= maxInclusive
                ? DataResult.success(value)
                : DataResult.error(() -> error.apply(value)));
    }

    private static Codec<Integer> hexColor(int expectedDigits) {
        long maxValue = (1L << expectedDigits * 4) - 1L;
        return Codec.STRING.comapFlatMap(string -> {
            if (!string.startsWith("#")) return DataResult.error(() -> "Hex color must begin with #");
            int digits = string.length() - 1;
            if (digits != expectedDigits)
                return DataResult.error(() -> "Hex color is wrong size, expected " + expectedDigits + " digits but got " + digits);
            try {
                long parsed = Long.parseLong(string.substring(1), 16);
                if (parsed < 0L || parsed > maxValue)
                    return DataResult.error(() -> "Hex color out of range: " + string);
                return DataResult.success((int) parsed);
            } catch (NumberFormatException exception) {
                return DataResult.error(() -> "Hex color is not a number: " + string);
            }
        }, value -> "#" + String.format("%0" + expectedDigits + "x", value));
    }
}
