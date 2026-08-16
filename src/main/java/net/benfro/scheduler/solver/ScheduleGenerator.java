package net.benfro.scheduler.solver;

import static net.benfro.scheduler.solver.SchedulingConstants.CLOSING_TIME;
import static net.benfro.scheduler.solver.SchedulingConstants.OPENING_TIME;
import static net.benfro.scheduler.solver.SchedulingConstants.SLOT_MINUTES;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import net.benfro.scheduler.domain.CoverageRequirement;
import net.benfro.scheduler.domain.Group;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.domain.TimeSlot;

/**
 * Builds the raw input for a {@code TeacherRoster}: the fixed 30-minute grid of the
 * opening day, one unassigned {@link TeacherSlot} per teacher per grid slot, and one
 * {@link CoverageRequirement} per (group, grid slot) where the group actually has a
 * pupil present.
 */
public final class ScheduleGenerator {

    private ScheduleGenerator() {
    }

    /** The fixed 07:00-17:30 grid of 30-minute {@link TimeSlot}s for one day. */
    public static List<TimeSlot> dailySlots(LocalDate date) {
        List<TimeSlot> slots = new ArrayList<>();
        LocalTime cursor = OPENING_TIME;
        while (cursor.isBefore(CLOSING_TIME)) {
            LocalTime next = cursor.plusMinutes(SLOT_MINUTES);
            slots.add(new TimeSlot(date, cursor, next));
            cursor = next;
        }
        return slots;
    }

    /** One unassigned {@link TeacherSlot} per grid slot of {@code date} for {@code teacher}. */
    public static List<TeacherSlot> teacherSlots(Teacher teacher, LocalDate date) {
        List<TeacherSlot> slots = new ArrayList<>();
        for (TimeSlot slot : dailySlots(date)) {
            slots.add(new TeacherSlot(teacher.name() + "@" + slot.date() + "T" + slot.start(), teacher, slot, null));
        }
        return slots;
    }

    public static List<TeacherSlot> teacherSlots(Teacher teacher, List<LocalDate> dates) {
        List<TeacherSlot> slots = new ArrayList<>();
        for (LocalDate date : dates) {
            slots.addAll(teacherSlots(teacher, date));
        }
        return slots;
    }

    /**
     * One {@link CoverageRequirement} per grid slot of {@code date} that overlaps at
     * least one pupil's staying time for that date, clipped to opening hours. A group
     * with no pupil present during a given slot has no requirement for it.
     */
    public static List<CoverageRequirement> coverageRequirements(Group group, LocalDate date) {
        List<TimeSlot> pupilPresence = group.pupils().stream()
                .flatMap(pupil -> pupil.stayingTimes().stream())
                .filter(stayingTime -> stayingTime.date().equals(date))
                .toList();

        return dailySlots(date).stream()
                .filter(gridSlot -> pupilPresence.stream().anyMatch(presence -> overlapsWithinOpeningHours(presence, gridSlot)))
                .map(gridSlot -> new CoverageRequirement(group, gridSlot))
                .toList();
    }

    public static List<CoverageRequirement> coverageRequirements(Group group, List<LocalDate> dates) {
        List<CoverageRequirement> requirements = new ArrayList<>();
        for (LocalDate date : dates) {
            requirements.addAll(coverageRequirements(group, date));
        }
        return requirements;
    }

    /** Inclusive list of dates from {@code startInclusive} to {@code endInclusive}. */
    public static List<LocalDate> dateRange(LocalDate startInclusive, LocalDate endInclusive) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = startInclusive; !date.isAfter(endInclusive); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private static boolean overlapsWithinOpeningHours(TimeSlot pupilPresence, TimeSlot gridSlot) {
        LocalTime start = clamp(pupilPresence.start());
        LocalTime end = clamp(pupilPresence.end());
        return start.isBefore(end) && start.isBefore(gridSlot.end()) && gridSlot.start().isBefore(end);
    }

    private static LocalTime clamp(LocalTime time) {
        if (time.isBefore(OPENING_TIME)) {
            return OPENING_TIME;
        }
        if (time.isAfter(CLOSING_TIME)) {
            return CLOSING_TIME;
        }
        return time;
    }
}
