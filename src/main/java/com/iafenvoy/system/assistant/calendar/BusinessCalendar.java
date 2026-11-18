package com.iafenvoy.system.assistant.calendar;

import com.iafenvoy.system.config.file.AgentConfig;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Calendar dates, never elapsed 24-hour durations; weeks start on Monday.
 */
public final class BusinessCalendar {
    private BusinessCalendar() {
    }

    public static ZoneId zone() {
        return ZoneId.of(AgentConfig.INSTANCE.get().timeZone());
    }

    public static LocalDate today() {
        return LocalDate.now(zone());
    }

    public static LocalDate date(long timestamp, ZoneId zone) {
        return Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
    }

    public static LocalDate week(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate month(LocalDate date) {
        return date.withDayOfMonth(1);
    }
}
