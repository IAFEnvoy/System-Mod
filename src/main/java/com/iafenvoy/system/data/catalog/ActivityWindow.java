package com.iafenvoy.system.data.catalog;

import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Calendar eligibility for new acquisitions. Overnight ranges belong to their starting weekday.
 */
public record ActivityWindow(Optional<LocalDate> startDate, Optional<LocalDate> endDate,
                             List<Integer> weekdays, List<Hours> hours) {
    public static final ActivityWindow ALWAYS = new ActivityWindow(Optional.empty(), Optional.empty(), List.of(), List.of());

    private static <T> Codec<T> temporal(Function<String, T> parse) {
        return Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(parse.apply(value));
            } catch (DateTimeException error) {
                return DataResult.error(() -> "Invalid calendar value: " + value);
            }
        }, Object::toString);
    }

    private static final Codec<LocalDate> DATE = temporal(LocalDate::parse).validate(value -> value.getYear() >= 1 && value.getYear() <= 9999
            ? DataResult.success(value) : DataResult.error(() -> "Activity dates must be within years 0001..9999"));
    private static final Codec<LocalTime> TIME = temporal(LocalTime::parse);
    public static final Codec<ActivityWindow> CODEC = RecordCodecBuilder.<ActivityWindow>create(i -> i.group(
            DATE.optionalFieldOf("start_date").forGetter(ActivityWindow::startDate),
            DATE.optionalFieldOf("end_date").forGetter(ActivityWindow::endDate),
            Codec.intRange(1, 7).listOf(0, 7).optionalFieldOf("weekdays", List.of()).forGetter(ActivityWindow::weekdays),
            Hours.CODEC.listOf(0, 16).optionalFieldOf("hours", List.of()).forGetter(ActivityWindow::hours)
    ).apply(i, ActivityWindow::new)).validate(value -> value.startDate.isPresent() && value.endDate.isPresent()
            && value.startDate.get().isAfter(value.endDate.get())
            ? DataResult.error(() -> "Activity start_date must not exceed end_date") : DataResult.success(value));

    public ActivityWindow {
        weekdays = List.copyOf(weekdays);
        hours = List.copyOf(hours);
    }

    public record Hours(LocalTime start, LocalTime end) {
        public static final Codec<Hours> CODEC = RecordCodecBuilder.<Hours>create(i -> i.group(
                TIME.fieldOf("start").forGetter(Hours::start), TIME.fieldOf("end").forGetter(Hours::end)
        ).apply(i, Hours::new)).validate(value -> value.start.equals(value.end)
                ? DataResult.error(() -> "Activity hours must have different start and end; omit hours for all day") : DataResult.success(value));
    }

    public enum State {OPEN, NOT_STARTED, CLOSED, ENDED}

    public record Status(State state, Optional<Instant> nextOpening) {
    }

    public Status status(Instant now, ZoneId zone) {
        if (this.equals(ALWAYS)) return new Status(State.OPEN, Optional.empty());
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate first = today.minusDays(1);
        if (this.startDate.isPresent() && first.isBefore(this.startDate.get())) first = this.startDate.get();
        Instant next = null;
        // A weekly schedule has a matching day within seven days; no world scans or unbounded date iteration.
        for (int offset = 0; offset < 9; offset++) {
            LocalDate day = first.plusDays(offset);
            if (this.endDate.isPresent() && day.isAfter(this.endDate.get())) break;
            if (!this.weekdays.isEmpty() && !this.weekdays.contains(day.getDayOfWeek().getValue())) continue;
            List<Hours> ranges = this.hours.isEmpty() ? List.of(new Hours(LocalTime.MIN, LocalTime.MIN)) : this.hours;
            for (Hours range : ranges) {
                Instant start = day.atTime(range.start).atZone(zone).toInstant();
                LocalDate endDay = !range.end.isAfter(range.start) ? day.plusDays(1) : day;
                Instant end = endDay.atTime(range.end).atZone(zone).toInstant();
                if (!now.isBefore(start) && now.isBefore(end)) return new Status(State.OPEN, Optional.empty());
                if (start.isAfter(now) && (next == null || start.isBefore(next))) next = start;
            }
        }
        State state = next == null ? State.ENDED
                : this.startDate.filter(day -> day.isAfter(today)).isPresent() ? State.NOT_STARTED : State.CLOSED;
        return new Status(state, Optional.ofNullable(next));
    }

    public boolean open() {
        return this.status(Instant.now(), BusinessCalendar.zone()).state == State.OPEN;
    }

    public Optional<Component> description() {
        if (this.equals(ALWAYS)) return Optional.empty();
        ZoneId zone = BusinessCalendar.zone();
        Status status = this.status(Instant.now(), zone);
        Component text = Component.translatable("system.activity." + status.state.name().toLowerCase(Locale.ROOT));
        return Optional.of(status.nextOpening.<Component>map(next -> text.copy().append(" · ").append(Component.translatable("system.activity.next",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone).format(next), zone.getId()))).orElse(text));
    }
}
