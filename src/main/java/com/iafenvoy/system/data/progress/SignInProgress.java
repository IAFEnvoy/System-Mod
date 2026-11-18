package com.iafenvoy.system.data.progress;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A date high-water mark prevents repeated grants when the server clock moves backwards.
 */
public record SignInProgress(long total, int streak, int longest, String lastDate, String zone,
                             String week, int weekly, String month, int monthly) {
    public static final SignInProgress EMPTY = new SignInProgress(0, 0, 0, "", "", "", 0, "", 0);
    public static final Codec<SignInProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("total").forGetter(SignInProgress::total),
            Codec.INT.fieldOf("streak").forGetter(SignInProgress::streak),
            Codec.INT.fieldOf("longest").forGetter(SignInProgress::longest),
            Codec.STRING.fieldOf("lastDate").forGetter(SignInProgress::lastDate),
            Codec.STRING.fieldOf("zone").forGetter(SignInProgress::zone),
            Codec.STRING.fieldOf("week").forGetter(SignInProgress::week),
            Codec.INT.fieldOf("weekly").forGetter(SignInProgress::weekly),
            Codec.STRING.fieldOf("month").forGetter(SignInProgress::month),
            Codec.INT.fieldOf("monthly").forGetter(SignInProgress::monthly)
    ).apply(i, SignInProgress::new));

    public SignInProgress next(LocalDate date, ZoneId zone) {
        if (!this.lastDate.isEmpty() && !date.isAfter(LocalDate.parse(this.lastDate)))
            throw new AgentException(AgentFailure.EXPIRED);
        boolean sameZone = this.zone.equals(zone.getId());
        int streak = sameZone && !this.lastDate.isEmpty() && date.minusDays(1).toString().equals(this.lastDate) ? Math.addExact(this.streak, 1) : 1;
        String week = BusinessCalendar.week(date).toString(), month = BusinessCalendar.month(date).toString();
        return new SignInProgress(Math.addExact(this.total, 1), streak, Math.max(this.longest, streak), date.toString(), zone.getId(),
                week, sameZone && week.equals(this.week) ? this.weekly + 1 : 1,
                month, sameZone && month.equals(this.month) ? this.monthly + 1 : 1);
    }

    public int currentStreak(LocalDate day, ZoneId zone) {
        return this.zone.equals(zone.getId()) && (day.toString().equals(this.lastDate) || day.minusDays(1).toString().equals(this.lastDate)) ? this.streak : 0;
    }

    public int currentWeek(LocalDate day, ZoneId zone) {
        return this.zone.equals(zone.getId()) && BusinessCalendar.week(day).toString().equals(this.week) ? this.weekly : 0;
    }

    public int currentMonth(LocalDate day, ZoneId zone) {
        return this.zone.equals(zone.getId()) && BusinessCalendar.month(day).toString().equals(this.month) ? this.monthly : 0;
    }
}
