package net.benfro.presched.solver.constraint;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import net.benfro.presched.domain.Group;
import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.Teacher;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.domain.TimeSlot;
import net.benfro.presched.solver.ScheduleGenerator;

/** Scenario-building helpers shared by every {@code *ConstraintsTest} in this package. */
final class ConstraintTestFixtures {

    static final LocalDate DATE = LocalDate.of(2026, 8, 17).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    private ConstraintTestFixtures() {
    }

    static TimeSlot morningSlot() {
        return new TimeSlot(DATE, LocalTime.of(9, 0), LocalTime.of(9, 30));
    }

    static TimeSlot afternoonSlot() {
        return new TimeSlot(DATE, LocalTime.of(14, 0), LocalTime.of(14, 30));
    }

    /** The first {@code count} grid slots of {@code date}, all set to {@code activity}. */
    static List<TeacherSlot> onDutySlots(Teacher teacher, LocalDate date, int count, SlotActivity activity) {
        List<TeacherSlot> slots = ScheduleGenerator.teacherSlots(teacher, date).subList(0, count);
        slots.forEach(slot -> slot.setActivity(activity));
        return new ArrayList<>(slots);
    }

    /** {@code teacher} teaching {@code group} for grid slots [{@code fromInclusive}, {@code toExclusive}) of {@code date}. */
    static List<TeacherSlot> teachingSlots(Group group, Teacher teacher, LocalDate date, int fromInclusive, int toExclusive) {
        List<TimeSlot> grid = ScheduleGenerator.dailySlots(date);
        List<TeacherSlot> slots = new ArrayList<>();
        for (int i = fromInclusive; i < toExclusive; i++) {
            slots.add(new TeacherSlot(teacher.name() + "-" + i, teacher, grid.get(i), new SlotActivity.Teaching(group)));
        }
        return slots;
    }

    /**
     * Planning-time slots for Monday through Thursday of the same ISO week (each count
     * capped at the 21 slots/day grid size), used to build up weekly totals beyond what
     * a single day's grid could hold.
     */
    static List<TeacherSlot> weekOfOnDutySlots(Teacher teacher, int mondaySlots, int tuesdaySlots,
            int wednesdaySlots, int thursdaySlots) {
        List<TeacherSlot> slots = new ArrayList<>();
        slots.addAll(onDutySlots(teacher, DATE, mondaySlots, SlotActivity.PLANNING_TIME));
        slots.addAll(onDutySlots(teacher, DATE.plusDays(1), tuesdaySlots, SlotActivity.PLANNING_TIME));
        slots.addAll(onDutySlots(teacher, DATE.plusDays(2), wednesdaySlots, SlotActivity.PLANNING_TIME));
        slots.addAll(onDutySlots(teacher, DATE.plusDays(3), thursdaySlots, SlotActivity.PLANNING_TIME));
        return slots;
    }
}
