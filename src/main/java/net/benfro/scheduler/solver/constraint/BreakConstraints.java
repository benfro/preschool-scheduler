package net.benfro.scheduler.solver.constraint;

import static net.benfro.scheduler.solver.SchedulingConstants.BREAK_REQUIRED_AFTER_MINUTES;
import static net.benfro.scheduler.solver.SchedulingConstants.MAX_BREAK_PERIODS_PER_DAY;
import static net.benfro.scheduler.solver.SchedulingConstants.SLOT_MINUTES;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;

import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.solver.DayShiftAnalysis;
import net.benfro.scheduler.solver.SchedulingConstants;
import net.benfro.scheduler.solver.SlotActivities;

/** Rules governing whether, how often, and where in a shift a teacher's breaks may fall. */
public final class BreakConstraints {

    private BreakConstraints() {
    }

    public static Constraint[] define(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                missingRequiredBreak(constraintFactory),
                tooManyBreakPeriods(constraintFactory),
                breakTooCloseToShiftEdge(constraintFactory),
                avoidStartingOrEndingShiftWithBreak(constraintFactory),
        };
    }

    /** A teacher on duty 6h+ in one day (any mix of teaching/break/planning) must include a break. */
    static Constraint missingRequiredBreak(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> SlotActivities.isOnDuty(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(),
                        ConstraintCollectors.toList(TeacherSlot::getActivity))
                .filter((teacher, date, activities) -> activities.size() * SLOT_MINUTES >= BREAK_REQUIRED_AFTER_MINUTES
                        && activities.stream().noneMatch(activity -> activity instanceof SlotActivity.Break))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Missing required break");
    }

    /**
     * A working day has at most {@link SchedulingConstants#MAX_BREAK_PERIODS_PER_DAY}
     * distinct break periods — a maximal run of consecutive {@code Break} slots counts as
     * one period no matter how long it is, but a third, fourth, etc. separate period
     * (breaking, working again, breaking again, working again...) is forbidden. Note
     * breaks never count toward {@link WorkingHoursConstraints} either way -
     * {@link SlotActivities#countsTowardWorkingHours} excludes them from the 8h/40h caps
     * regardless of how many periods there are.
     */
    static Constraint tooManyBreakPeriods(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).breakPeriodCount() > MAX_BREAK_PERIODS_PER_DAY)
                .penalize(HardSoftScore.ONE_HARD,
                        (teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).breakPeriodCount() - MAX_BREAK_PERIODS_PER_DAY)
                .asConstraint("Too many break periods in a day");
    }

    /**
     * A {@link SlotActivity.Break} may not land within
     * {@link SchedulingConstants#BREAK_EDGE_BUFFER_SLOTS} on-duty slots of a teacher's
     * shift start, nor within that many on-duty slots of the shift's end — a break must
     * sit at least 2h into the shift and at least 2h before it finishes. Stronger than (and
     * a hard superset of) {@link #avoidStartingOrEndingShiftWithBreak}, which only
     * forbids the exact first/last slot.
     */
    static Constraint breakTooCloseToShiftEdge(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).edgeBreakSlotCount() > 0)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).edgeBreakSlotCount())
                .asConstraint("Break too close to shift edge");
    }

    /**
     * Prefer a teacher's day not to start or end with a {@link SlotActivity.Break} —
     * clocking in only to immediately go on break, or taking a break right before
     * clocking off, reads as wasted/awkward scheduling. Penalized once per bad edge (so
     * up to 2 if both ends are breaks); a single-slot shift that's itself a break counts
     * once, not twice.
     */
    static Constraint avoidStartingOrEndingShiftWithBreak(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).shiftBreakEdgeCount() > 0)
                .penalize(HardSoftScore.ONE_SOFT, (teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).shiftBreakEdgeCount())
                .asConstraint("Avoid starting or ending shift with a break");
    }
}
