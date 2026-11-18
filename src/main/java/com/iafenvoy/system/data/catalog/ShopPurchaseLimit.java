package com.iafenvoy.system.data.catalog;

import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Per-player offer purchases, counted by complete bundles rather than individual reward items.
 */
public record ShopPurchaseLimit(int count, Period period) {
    public static final Codec<ShopPurchaseLimit> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(ShopPurchaseLimit::count),
            Period.CODEC.optionalFieldOf("period", Period.LIFETIME).forGetter(ShopPurchaseLimit::period)
    ).apply(i, ShopPurchaseLimit::new));

    public enum Period implements StringRepresentable {
        LIFETIME, DAILY, WEEKLY, MONTHLY;

        public static final Codec<Period> CODEC = StringRepresentable.fromEnum(Period::values);

        public long start(long now, ZoneId zone) {
            if (this == LIFETIME) return Long.MIN_VALUE;
            LocalDate day = BusinessCalendar.date(now, zone);
            return (switch (this) {
                case WEEKLY -> BusinessCalendar.week(day);
                case MONTHLY -> BusinessCalendar.month(day);
                default -> day;
            }).atStartOfDay(zone).toInstant().toEpochMilli();
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
