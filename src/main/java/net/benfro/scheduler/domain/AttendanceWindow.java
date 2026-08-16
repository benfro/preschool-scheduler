package net.benfro.scheduler.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * When one {@link Pupil} is present on one day - an arbitrary-length interval, unlike
 * {@link TimeSlot}, which is always exactly one 30-minute grid granule aligned to the
 * schedule's opening-hours grid. Kept as its own type (rather than reusing {@link TimeSlot}
 * for both) because the two don't share the same invariants: a staying time can start/end at
 * any minute and even run outside opening hours (clipped later by
 * {@code ScheduleGenerator#coverageRequirements}), while a {@link TimeSlot} never can.
 */
public record AttendanceWindow(LocalDate date, LocalTime start, LocalTime end) {
}
