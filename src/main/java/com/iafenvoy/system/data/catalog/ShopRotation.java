package com.iafenvoy.system.data.catalog;

import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Calendar-based fixed stock selection; no reroll occurs when browsing or searching. */
public record ShopRotation(Period period, int count, boolean perPlayer, long seed) {
    public static final Codec<ShopRotation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Period.CODEC.optionalFieldOf("period", Period.DAILY).forGetter(ShopRotation::period),
            Codec.intRange(1, 4096).fieldOf("count").forGetter(ShopRotation::count),
            Codec.BOOL.optionalFieldOf("per_player", false).forGetter(ShopRotation::perPlayer),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(ShopRotation::seed)
    ).apply(i, ShopRotation::new));

    public enum Period implements StringRepresentable {
        DAILY, WEEKLY, MONTHLY;
        public static final Codec<Period> CODEC = StringRepresentable.fromEnum(Period::values);

        public LocalDate start(long time, ZoneId zone) {
            LocalDate date = BusinessCalendar.date(time, zone);
            return switch (this) {
                case DAILY -> date;
                case WEEKLY -> BusinessCalendar.week(date);
                case MONTHLY -> BusinessCalendar.month(date);
            };
        }

        public long next(long time, ZoneId zone) {
            LocalDate start = this.start(time, zone);
            return (switch (this) {
                case DAILY -> start.plusDays(1);
                case WEEKLY -> start.plusWeeks(1);
                case MONTHLY -> start.plusMonths(1);
            }).atStartOfDay(zone).toInstant().toEpochMilli();
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
