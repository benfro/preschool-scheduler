package net.benfro.scheduler.solver.constraint;

import static net.benfro.scheduler.solver.SchedulingConstants.MAX_BREAK_ALLOWANCE_MINUTES;
import static net.benfro.scheduler.solver.SchedulingConstants.MAX_PLANNING_SESSIONS_PER_DAY;
import static net.benfro.scheduler.solver.SchedulingConstants.MINUTES_PER_HOUR;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;

import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.solver.DayShiftAnalysis;
import net.benfro.scheduler.solver.SchedulingConstants;
import net.benfro.scheduler.solver.WeekPlanningAnalysis;

/** Hard rules on the overall shape of a teacher's day: contiguity, total span, and planning-session length. */
public final class ShiftShapeConstraints {

    private ShiftShapeConstraints() {
    }

    public static Constraint[] define(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                shiftMustBeContiguous(constraintFactory),
                shiftSpanExceedsCap(constraintFactory),
                planningSessionTooShort(constraintFactory),
                planningSessionTooLong(constraintFactory),
                tooManyPlanningSessionsPerDay(constraintFactory),
                planningSessionsNeedWorkdayGap(constraintFactory),
        };
    }

    /**
     * A teacher's on-duty time on one day must form a single contiguous block — no
     * {@link SlotActivity#OFF_DUTY} slots sandwiched between two on-duty slots. Breaks
     * and planning time count as "on duty" for this purpose (they don't break
     * contiguity), so a shift can still legally contain a break; only clocking off and
     * back on again the same day is forbidden. A teacher not working a given day at all
     * (zero on-duty slots) is unaffected.
     */
    static Constraint shiftMustBeContiguous(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, TeacherSlot::date, ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).gapSlotCount() > 0)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).gapSlotCount())
                .asConstraint("Shift must be contiguous");
    }

    /**
     * A teacher's clock-in-to-clock-out span on one day (first on-duty slot to last,
     * inclusive of any embedded breaks) must not exceed their daily on-duty cap plus one
     * reasonable break's worth of slack. Without this, {@link #shiftMustBeContiguous}
     * alone is satisfied just as well by a shift that's technically gap-free but padded
     * with unlimited break time across the entire opening window — gap-free, but not a
     * real bounded shift.
     */
    static Constraint shiftSpanExceedsCap(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, TeacherSlot::date, ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).shiftSpanMinutes()
                        > teacher.dailyHoursMax() * MINUTES_PER_HOUR + MAX_BREAK_ALLOWANCE_MINUTES)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).shiftSpanMinutes()
                        - (teacher.dailyHoursMax() * MINUTES_PER_HOUR + MAX_BREAK_ALLOWANCE_MINUTES))
                .asConstraint("Shift span exceeds cap");
    }

    /**
     * A {@link SlotActivity.PlanningTime} session — a maximal run of consecutive planning
     * slots in a day — must be at least {@code MIN_PLANNING_SESSION_SLOTS} slots long. A
     * lone isolated half-hour of planning time, surrounded by teaching, breaks or
     * off-duty, is not a usable planning session.
     */
    static Constraint planningSessionTooShort(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, TeacherSlot::date, ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).shortPlanningSlotCount() > 0)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).shortPlanningSlotCount())
                .asConstraint("Planning session too short");
    }

    /**
     * A {@link SlotActivity.PlanningTime} session — a maximal run of consecutive planning
     * slots in a day — must be at most {@link SchedulingConstants#MAX_PLANNING_SESSION_SLOTS}
     * slots long. Combined with {@link #planningSessionTooShort}, a planning session is
     * always exactly {@code MAX_PLANNING_SESSION_SLOTS} (1h) slots, never a sprawling
     * multi-hour block.
     */
    static Constraint planningSessionTooLong(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, TeacherSlot::date, ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).longPlanningSlotCount() > 0)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> (int) DayShiftAnalysis.of(daySlots).longPlanningSlotCount())
                .asConstraint("Planning session too long");
    }

    /**
     * A working day may contain at most {@link SchedulingConstants#MAX_PLANNING_SESSIONS_PER_DAY}
     * (1) distinct {@link SlotActivity.PlanningTime} session — a maximal run of consecutive
     * planning slots counts as one session no matter how long it is, but splitting planning
     * time into two or more separate sessions the same day (planning, teaching, planning
     * again) is forbidden.
     */
    static Constraint tooManyPlanningSessionsPerDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, TeacherSlot::date, ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> DayShiftAnalysis.of(daySlots).planningSessionCount() > MAX_PLANNING_SESSIONS_PER_DAY)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) ->
                        (int) (DayShiftAnalysis.of(daySlots).planningSessionCount() - MAX_PLANNING_SESSIONS_PER_DAY))
                .asConstraint("Too many planning sessions in a day");
    }

    /**
     * A teacher's planning sessions must have at least one workday between them - two
     * planning-session days may not be calendar-adjacent workdays. See
     * {@link WeekPlanningAnalysis} for what "workday" means here and why plain calendar
     * adjacency is the right check.
     */
    static Constraint planningSessionsNeedWorkdayGap(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, ConstraintCollectors.toList())
                .filter((teacher, teacherSlots) -> WeekPlanningAnalysis.adjacentPlanningDayCount(teacherSlots) > 0)
                .penalize(HardSoftScore.ONE_HARD, (teacher, teacherSlots) -> (int) WeekPlanningAnalysis.adjacentPlanningDayCount(teacherSlots))
                .asConstraint("Planning sessions need a workday between them");
    }
}
