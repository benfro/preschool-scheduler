package net.benfro.scheduler.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Problem fact: {@code group} needs at least one teacher actively teaching it during
 * {@code slot}. Generated only for slots where the group actually has a pupil present
 * (see {@code ScheduleGenerator#coverageRequirements}), not blanket opening hours.
 */
public record CoverageRequirement(Group group, TimeSlot slot) {

    /** Delegates to {@code slot.date()} - lets callers ask a requirement its own date without reaching through {@link #slot()}. */
    public LocalDate date() {
        return slot.date();
    }

    /** Delegates to {@code slot.start()} - lets callers ask a requirement its own start time without reaching through {@link #slot()}. */
    public LocalTime start() {
        return slot.start();
    }
}
