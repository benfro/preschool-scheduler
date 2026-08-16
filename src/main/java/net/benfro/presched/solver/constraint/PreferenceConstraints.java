package net.benfro.presched.solver.constraint;

import static net.benfro.presched.solver.SchedulingConstants.MAX_TEACHING_SLOTS_PER_TEACHER_PER_DAY;
import static net.benfro.presched.solver.SchedulingConstants.MINUTES_PER_HOUR;
import static net.benfro.presched.solver.SchedulingConstants.PLANNING_TARGET_WEIGHT;
import static net.benfro.presched.solver.SchedulingConstants.SLOT_MINUTES;
import static net.benfro.presched.solver.SchedulingConstants.WEEKLY_PLANNING_TIME_TARGET_MINUTES;

import java.util.List;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;

import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.solver.IsoWeek;
import net.benfro.presched.solver.SlotActivities;

/**
 * Soft preferences that aren't about hard limits or fairness-between-teachers: which
 * group a teacher ideally teaches, how close to their weekly hour/planning targets they
 * land, and avoiding avoidable teacher handoffs within one group's day.
 */
public final class PreferenceConstraints {

    private PreferenceConstraints() {
    }

    public static Constraint[] define(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                minimizeDistinctTeachersPerGroupPerDay(constraintFactory),
                preferHomeGroupAssignment(constraintFactory),
                weeklyPlanningTimeOffTarget(constraintFactory),
                weeklyOnDutyBelowTarget(constraintFactory),
        };
    }

    /**
     * Prefer as few different teachers as possible touching one group on one day
     * (continuity) — but only penalize teachers beyond the minimum the day's actual
     * teaching load requires. A group whose pupils are present the full 07:00-17:30
     * window needs 2+ teachers no matter what (no single teacher can legally cover
     * 10.5h); that's not fragmentation, so it isn't penalized. Only distinct teachers
     * *beyond* that structural minimum are treated as avoidable churn.
     */
    static Constraint minimizeDistinctTeachersPerGroupPerDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> teacherSlot.getActivity() instanceof SlotActivity.Teaching)
                .groupBy(SlotActivities::teachingGroupOf, teacherSlot -> teacherSlot.getSlot().date(),
                        ConstraintCollectors.count(), ConstraintCollectors.countDistinct(TeacherSlot::getTeacher))
                .filter((group, date, teachingSlotCount, teacherCount) -> teacherCount > minimumTeachersNeeded(teachingSlotCount))
                .penalize(HardSoftScore.ONE_SOFT,
                        (group, date, teachingSlotCount, teacherCount) -> teacherCount - minimumTeachersNeeded(teachingSlotCount))
                .asConstraint("Minimize distinct teachers per group per day");
    }

    /** Prefer assigning a teacher to their own home group over someone else's. */
    static Constraint preferHomeGroupAssignment(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> teacherSlot.getActivity() instanceof SlotActivity.Teaching teaching
                        && teaching.group().equals(teacherSlot.getTeacher().group()))
                .reward(HardSoftScore.ONE_SOFT)
                .asConstraint("Prefer home group assignment");
    }

    /**
     * Prefer a teacher who works at all in an ISO week to land as close as possible to
     * exactly the weekly planning-time target — not merely "at least". A one-sided floor
     * (only penalizing a shortfall) left the ceiling completely unguarded: planning time
     * costs nothing extra beyond the ordinary daily/weekly hour caps, so the solver was
     * free to substitute it for break time wherever convenient (e.g. to dodge
     * {@code BreakConstraints.avoidStartingOrEndingShiftWithBreak}) and happily
     * over-deliver by hours. Penalizing the absolute deviation in both directions pulls
     * it back to the target.
     *
     * <p>Soft rather than hard: as a hard <em>floor</em> this created a scoring cliff — a
     * teacher with zero on-duty slots was exempt entirely (filtered out below), but their
     * very first on-duty slot instantly cost the full shortfall (target - 0) regardless of
     * how many on-duty slots they ended up with. That dwarfed the one-point-per-slot
     * coverage-gap penalty and made both construction heuristic and local search
     * rationally refuse to ever schedule anyone. Symmetric deviation removes that jump
     * (going from 0 to 1 planning slot now smoothly reduces the penalty), but it's kept
     * soft regardless so it can never outrank coverage or the hour caps.
     */
    static Constraint weeklyPlanningTimeOffTarget(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> SlotActivities.isOnDuty(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> IsoWeek.of(teacherSlot.getSlot().date()),
                        ConstraintCollectors.toList(TeacherSlot::getActivity))
                .filter((teacher, week, activities) -> planningMinutes(activities) != WEEKLY_PLANNING_TIME_TARGET_MINUTES)
                .penalize(HardSoftScore.ONE_SOFT,
                        (teacher, week, activities) -> Math.abs(planningMinutes(activities) - WEEKLY_PLANNING_TIME_TARGET_MINUTES)
                                * PLANNING_TARGET_WEIGHT)
                .asConstraint("Weekly planning time off target");
    }

    /**
     * Prefer a teacher who works at all in an ISO week to use as much of their
     * contracted weekly hours ({@code Teacher.hoursPerWeek()}) as possible — paid on-duty
     * time (teaching + planning; breaks are unpaid and never counted, see
     * {@link SlotActivities#countsTowardWorkingHours}) should sit close to that number,
     * not well under it. {@code WorkingHoursConstraints.teacherWeeklyHoursExceeded}
     * already forbids going over that same number as a hard ceiling, so this can never
     * push a teacher past their cap — it just discourages leaving it mostly unused. Soft,
     * since full utilization isn't always achievable (or even desirable) depending on how
     * much coverage the week actually needs; this is a preference for fuller schedules,
     * not a guarantee of one.
     */
    static Constraint weeklyOnDutyBelowTarget(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> SlotActivities.isOnDuty(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> IsoWeek.of(teacherSlot.getSlot().date()),
                        ConstraintCollectors.toList(TeacherSlot::getActivity))
                .filter((teacher, week, activities) -> workingMinutes(activities) < teacher.hoursPerWeek() * MINUTES_PER_HOUR)
                .penalize(HardSoftScore.ONE_SOFT,
                        (teacher, week, activities) -> teacher.hoursPerWeek() * MINUTES_PER_HOUR - workingMinutes(activities))
                .asConstraint("Weekly on-duty time below target");
    }

    /** Fewest teachers that could possibly deliver this many half-hour slots of teaching in a day. */
    private static long minimumTeachersNeeded(long teachingSlotCount) {
        return (teachingSlotCount + MAX_TEACHING_SLOTS_PER_TEACHER_PER_DAY - 1) / MAX_TEACHING_SLOTS_PER_TEACHER_PER_DAY;
    }

    private static int planningMinutes(List<SlotActivity> activities) {
        return (int) activities.stream().filter(activity -> activity instanceof SlotActivity.PlanningTime).count() * SLOT_MINUTES;
    }

    /** Minutes counted toward the daily/weekly hour caps — teaching + planning only, per {@link SlotActivities#countsTowardWorkingHours}. */
    private static int workingMinutes(List<SlotActivity> activities) {
        return (int) activities.stream().filter(SlotActivities::countsTowardWorkingHours).count() * SLOT_MINUTES;
    }
}
