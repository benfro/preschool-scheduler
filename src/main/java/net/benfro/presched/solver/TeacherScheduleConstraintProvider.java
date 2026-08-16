package net.benfro.presched.solver;

import static net.benfro.presched.solver.SchedulingConstants.BREAK_EDGE_BUFFER_SLOTS;
import static net.benfro.presched.solver.SchedulingConstants.BREAK_REQUIRED_AFTER_MINUTES;
import static net.benfro.presched.solver.SchedulingConstants.MAX_BREAK_ALLOWANCE_MINUTES;
import static net.benfro.presched.solver.SchedulingConstants.MAX_BREAK_PERIODS_PER_DAY;
import static net.benfro.presched.solver.SchedulingConstants.MAX_TEACHING_SLOTS_PER_TEACHER_PER_DAY;
import static net.benfro.presched.solver.SchedulingConstants.MIDDAY;
import static net.benfro.presched.solver.SchedulingConstants.MIN_PLANNING_SESSION_SLOTS;
import static net.benfro.presched.solver.SchedulingConstants.MINUTES_PER_HOUR;
import static net.benfro.presched.solver.SchedulingConstants.SLOT_MINUTES;
import static net.benfro.presched.solver.SchedulingConstants.PLANNING_TARGET_WEIGHT;
import static net.benfro.presched.solver.SchedulingConstants.WEEKLY_PLANNING_TIME_TARGET_MINUTES;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.List;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.common.LoadBalance;

import net.benfro.presched.domain.CoverageRequirement;
import net.benfro.presched.domain.Group;
import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.TeacherSlot;

public class TeacherScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                groupCoverageGap(constraintFactory),
                teacherDailyHoursExceeded(constraintFactory),
                teacherWeeklyHoursExceeded(constraintFactory),
                missingRequiredBreak(constraintFactory),
                tooManyBreakPeriods(constraintFactory),
                shiftMustBeContiguous(constraintFactory),
                shiftSpanExceedsCap(constraintFactory),
                planningSessionTooShort(constraintFactory),
                breakTooCloseToShiftEdge(constraintFactory),
                minimizeDistinctTeachersPerGroupPerDay(constraintFactory),
                preferHomeGroupAssignment(constraintFactory),
                weeklyPlanningTimeOffTarget(constraintFactory),
                weeklyOnDutyBelowTarget(constraintFactory),
                balanceEarlySlotsAcrossTeachers(constraintFactory),
                balanceLateSlotsAcrossTeachers(constraintFactory),
                avoidStartingOrEndingShiftWithBreak(constraintFactory),
        };
    }

    // ************************************************************************
    // Hard constraints
    // ************************************************************************

    /**
     * Every {@link CoverageRequirement} must be met by a teacher actually teaching that
     * group then — {@link SlotActivity.Teaching}, specifically. This is what guarantees a
     * group is never left without care: it doesn't matter how many teachers have that
     * group as their home group or are otherwise attached to it — if every one of them is
     * on a {@link SlotActivity.Break} or {@link SlotActivity.PlanningTime} at the same
     * moment a pupil is present, that moment still has zero matching {@link TeacherSlot}
     * and is penalized exactly like having no teacher assigned at all.
     */
    Constraint groupCoverageGap(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(CoverageRequirement.class)
                .ifNotExists(TeacherSlot.class,
                        Joiners.equal(CoverageRequirement::slot, TeacherSlot::getSlot),
                        Joiners.equal(CoverageRequirement::group, TeacherScheduleConstraintProvider::teachingGroupOf))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group coverage gap");
    }

    /** A teacher's on-duty (teaching + planning) time in one day must not exceed their daily cap. */
    Constraint teacherDailyHoursExceeded(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> countsTowardWorkingHours(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.count())
                .filter((teacher, date, slotCount) -> slotCount * SLOT_MINUTES > teacher.dailyHoursMax() * MINUTES_PER_HOUR)
                .penalize(HardSoftScore.ONE_HARD,
                        (teacher, date, slotCount) -> slotCount * SLOT_MINUTES - teacher.dailyHoursMax() * MINUTES_PER_HOUR)
                .asConstraint("Teacher daily hours exceeded");
    }

    /** A teacher's on-duty (teaching + planning) time in one ISO week must not exceed their weekly cap. */
    Constraint teacherWeeklyHoursExceeded(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> countsTowardWorkingHours(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> weekOf(teacherSlot.getSlot().date()), ConstraintCollectors.count())
                .filter((teacher, week, slotCount) -> slotCount * SLOT_MINUTES > teacher.hoursPerWeek() * MINUTES_PER_HOUR)
                .penalize(HardSoftScore.ONE_HARD,
                        (teacher, week, slotCount) -> slotCount * SLOT_MINUTES - teacher.hoursPerWeek() * MINUTES_PER_HOUR)
                .asConstraint("Teacher weekly hours exceeded");
    }

    /** A teacher on duty 6h+ in one day (any mix of teaching/break/planning) must include a break. */
    Constraint missingRequiredBreak(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> isOnDuty(teacherSlot.getActivity()))
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
     * breaks never count toward {@link #teacherDailyHoursExceeded}/
     * {@link #teacherWeeklyHoursExceeded} either way — {@link #countsTowardWorkingHours}
     * excludes them from the 8h/40h caps regardless of how many periods there are.
     */
    Constraint tooManyBreakPeriods(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> breakPeriodCount(daySlots) > MAX_BREAK_PERIODS_PER_DAY)
                .penalize(HardSoftScore.ONE_HARD,
                        (teacher, date, daySlots) -> breakPeriodCount(daySlots) - MAX_BREAK_PERIODS_PER_DAY)
                .asConstraint("Too many break periods in a day");
    }

    /**
     * A teacher's on-duty time on one day must form a single contiguous block — no
     * {@link SlotActivity#OFF_DUTY} slots sandwiched between two on-duty slots. Breaks
     * and planning time count as "on duty" for this purpose (they don't break
     * contiguity), so a shift can still legally contain a break; only clocking off and
     * back on again the same day is forbidden. A teacher not working a given day at all
     * (zero on-duty slots) is unaffected.
     */
    Constraint shiftMustBeContiguous(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> gapSlotCount(daySlots) > 0)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> gapSlotCount(daySlots))
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
    Constraint shiftSpanExceedsCap(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> shiftSpanMinutes(daySlots) > teacher.dailyHoursMax() * MINUTES_PER_HOUR + MAX_BREAK_ALLOWANCE_MINUTES)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> shiftSpanMinutes(daySlots)
                        - (teacher.dailyHoursMax() * MINUTES_PER_HOUR + MAX_BREAK_ALLOWANCE_MINUTES))
                .asConstraint("Shift span exceeds cap");
    }

    /**
     * A {@link SlotActivity.PlanningTime} session — a maximal run of consecutive planning
     * slots in a day — must be at least {@link SchedulingConstants#MIN_PLANNING_SESSION_SLOTS}
     * slots long. A lone isolated half-hour of planning time, surrounded by teaching,
     * breaks or off-duty, is not a usable planning session.
     */
    Constraint planningSessionTooShort(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> shortPlanningSlotCount(daySlots) > 0)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> shortPlanningSlotCount(daySlots))
                .asConstraint("Planning session too short");
    }

    /**
     * A {@link SlotActivity.Break} may not land within
     * {@link SchedulingConstants#BREAK_EDGE_BUFFER_SLOTS} on-duty slots of a teacher's
     * shift start, nor within that many on-duty slots of the shift's end — a break must
     * sit at least 2h into the shift and at least 2h before it finishes. Stronger than (and
     * a hard superset of) {@link #avoidStartingOrEndingShiftWithBreak}, which only
     * forbids the exact first/last slot.
     */
    Constraint breakTooCloseToShiftEdge(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> edgeBreakSlotCount(daySlots) > 0)
                .penalize(HardSoftScore.ONE_HARD, (teacher, date, daySlots) -> edgeBreakSlotCount(daySlots))
                .asConstraint("Break too close to shift edge");
    }

    // ************************************************************************
    // Soft constraints
    // ************************************************************************

    /**
     * Prefer as few different teachers as possible touching one group on one day
     * (continuity) — but only penalize teachers beyond the minimum the day's actual
     * teaching load requires. A group whose pupils are present the full 07:00-17:30
     * window needs 2+ teachers no matter what (no single teacher can legally cover
     * 10.5h); that's not fragmentation, so it isn't penalized. Only distinct teachers
     * *beyond* that structural minimum are treated as avoidable churn.
     */
    Constraint minimizeDistinctTeachersPerGroupPerDay(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> teacherSlot.getActivity() instanceof SlotActivity.Teaching)
                .groupBy(TeacherScheduleConstraintProvider::teachingGroupOf, teacherSlot -> teacherSlot.getSlot().date(),
                        ConstraintCollectors.count(), ConstraintCollectors.countDistinct(TeacherSlot::getTeacher))
                .filter((group, date, teachingSlotCount, teacherCount) -> teacherCount > minimumTeachersNeeded(teachingSlotCount))
                .penalize(HardSoftScore.ONE_SOFT,
                        (group, date, teachingSlotCount, teacherCount) -> teacherCount - minimumTeachersNeeded(teachingSlotCount))
                .asConstraint("Minimize distinct teachers per group per day");
    }

    /** Prefer assigning a teacher to their own home group over someone else's. */
    Constraint preferHomeGroupAssignment(ConstraintFactory constraintFactory) {
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
     * {@link #avoidStartingOrEndingShiftWithBreak}) and happily over-deliver by hours.
     * Penalizing the absolute deviation in both directions pulls it back to the target.
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
    Constraint weeklyPlanningTimeOffTarget(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> isOnDuty(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> weekOf(teacherSlot.getSlot().date()),
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
     * {@link #countsTowardWorkingHours}) should sit close to that number, not well under
     * it. {@link #teacherWeeklyHoursExceeded} already forbids going over that same number
     * as a hard ceiling, so this can never push a teacher past their cap — it just
     * discourages leaving it mostly unused. Soft, since full utilization isn't always
     * achievable (or even desirable) depending on how much coverage the week actually
     * needs; this is a preference for fuller schedules, not a guarantee of one.
     */
    Constraint weeklyOnDutyBelowTarget(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> isOnDuty(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> weekOf(teacherSlot.getSlot().date()),
                        ConstraintCollectors.toList(TeacherSlot::getActivity))
                .filter((teacher, week, activities) -> workingMinutes(activities) < teacher.hoursPerWeek() * MINUTES_PER_HOUR)
                .penalize(HardSoftScore.ONE_SOFT,
                        (teacher, week, activities) -> teacher.hoursPerWeek() * MINUTES_PER_HOUR - workingMinutes(activities))
                .asConstraint("Weekly on-duty time below target");
    }

    /** Prefer an even spread of before-{@link SchedulingConstants#MIDDAY} teaching slots across teachers. */
    Constraint balanceEarlySlotsAcrossTeachers(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> teacherSlot.getActivity() instanceof SlotActivity.Teaching
                        && teacherSlot.getSlot().start().isBefore(MIDDAY))
                .groupBy(ConstraintCollectors.loadBalance(TeacherSlot::getTeacher))
                .penalize(HardSoftScore.ONE_SOFT, TeacherScheduleConstraintProvider::unfairnessScore)
                .asConstraint("Balance early slots across teachers");
    }

    /** Prefer an even spread of {@link SchedulingConstants#MIDDAY}-or-later teaching slots across teachers. */
    Constraint balanceLateSlotsAcrossTeachers(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> teacherSlot.getActivity() instanceof SlotActivity.Teaching
                        && !teacherSlot.getSlot().start().isBefore(MIDDAY))
                .groupBy(ConstraintCollectors.loadBalance(TeacherSlot::getTeacher))
                .penalize(HardSoftScore.ONE_SOFT, TeacherScheduleConstraintProvider::unfairnessScore)
                .asConstraint("Balance late slots across teachers");
    }

    /**
     * Prefer a teacher's day not to start or end with a {@link SlotActivity.Break} —
     * clocking in only to immediately go on break, or taking a break right before
     * clocking off, reads as wasted/awkward scheduling. Penalized once per bad edge (so
     * up to 2 if both ends are breaks); a single-slot shift that's itself a break counts
     * once, not twice.
     */
    Constraint avoidStartingOrEndingShiftWithBreak(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> teacherSlot.getSlot().date(), ConstraintCollectors.toList())
                .filter((teacher, date, daySlots) -> shiftBreakEdgeCount(daySlots) > 0)
                .penalize(HardSoftScore.ONE_SOFT, (teacher, date, daySlots) -> shiftBreakEdgeCount(daySlots))
                .asConstraint("Avoid starting or ending shift with a break");
    }

    // ************************************************************************
    // Helpers
    // ************************************************************************

    private static boolean countsTowardWorkingHours(SlotActivity activity) {
        return activity instanceof SlotActivity.Teaching || activity instanceof SlotActivity.PlanningTime;
    }

    /** On duty covers teaching, break and planning time — everything except {@link SlotActivity#OFF_DUTY}. */
    private static boolean isOnDuty(SlotActivity activity) {
        return activity instanceof SlotActivity.Teaching
                || activity instanceof SlotActivity.Break
                || activity instanceof SlotActivity.PlanningTime;
    }

    private static Group teachingGroupOf(TeacherSlot teacherSlot) {
        return teacherSlot.getActivity() instanceof SlotActivity.Teaching teaching ? teaching.group() : null;
    }

    /** Fewest teachers that could possibly deliver this many half-hour slots of teaching in a day. */
    private static long minimumTeachersNeeded(long teachingSlotCount) {
        return (teachingSlotCount + MAX_TEACHING_SLOTS_PER_TEACHER_PER_DAY - 1) / MAX_TEACHING_SLOTS_PER_TEACHER_PER_DAY;
    }

    private static int planningMinutes(List<SlotActivity> activities) {
        return (int) activities.stream().filter(activity -> activity instanceof SlotActivity.PlanningTime).count() * SLOT_MINUTES;
    }

    /** Minutes counted toward the daily/weekly hour caps — teaching + planning only, per {@link #countsTowardWorkingHours}. */
    private static int workingMinutes(List<SlotActivity> activities) {
        return (int) activities.stream().filter(TeacherScheduleConstraintProvider::countsTowardWorkingHours).count() * SLOT_MINUTES;
    }

    /**
     * Number of off-duty slots strictly between a teacher's first and last on-duty slot
     * that day (time-sorted). Zero if the teacher didn't work that day, or worked one
     * unbroken block.
     */
    private static long gapSlotCount(List<TeacherSlot> daySlots) {
        List<SlotActivity> sortedActivities = sortedActivitiesOf(daySlots);
        int[] onDutyRange = onDutyRange(sortedActivities);
        if (onDutyRange[0] == -1) {
            return 0;
        }
        long gaps = 0;
        for (int i = onDutyRange[0]; i <= onDutyRange[1]; i++) {
            if (!isOnDuty(sortedActivities.get(i))) {
                gaps++;
            }
        }
        return gaps;
    }

    /** Minutes from a teacher's first on-duty slot to their last that day, inclusive. Zero if they didn't work. */
    private static long shiftSpanMinutes(List<TeacherSlot> daySlots) {
        int[] onDutyRange = onDutyRange(sortedActivitiesOf(daySlots));
        if (onDutyRange[0] == -1) {
            return 0;
        }
        return (long) (onDutyRange[1] - onDutyRange[0] + 1) * SLOT_MINUTES;
    }

    /**
     * How many of the shift's two edges (first on-duty slot, last on-duty slot) are a
     * {@link SlotActivity.Break} — 0, 1, or 2. A single-slot shift that's a break counts
     * once (it's one bad edge, not two). Zero if the teacher didn't work that day.
     */
    private static long shiftBreakEdgeCount(List<TeacherSlot> daySlots) {
        List<SlotActivity> sortedActivities = sortedActivitiesOf(daySlots);
        int[] onDutyRange = onDutyRange(sortedActivities);
        if (onDutyRange[0] == -1) {
            return 0;
        }
        long count = 0;
        if (sortedActivities.get(onDutyRange[0]) instanceof SlotActivity.Break) {
            count++;
        }
        if (onDutyRange[1] != onDutyRange[0] && sortedActivities.get(onDutyRange[1]) instanceof SlotActivity.Break) {
            count++;
        }
        return count;
    }

    /**
     * How many slots short of {@link SchedulingConstants#MIN_PLANNING_SESSION_SLOTS} each
     * too-short {@code PlanningTime} run (maximal run of consecutive planning slots) is,
     * summed across the day. E.g. one lone 1-slot run with a minimum of 2 contributes 1.
     */
    private static long shortPlanningSlotCount(List<TeacherSlot> daySlots) {
        List<SlotActivity> sortedActivities = sortedActivitiesOf(daySlots);
        long shortfall = 0;
        int runLength = 0;
        for (SlotActivity activity : sortedActivities) {
            if (activity instanceof SlotActivity.PlanningTime) {
                runLength++;
            } else {
                shortfall += shortfallFor(runLength);
                runLength = 0;
            }
        }
        shortfall += shortfallFor(runLength);
        return shortfall;
    }

    private static long shortfallFor(int runLength) {
        return runLength > 0 && runLength < MIN_PLANNING_SESSION_SLOTS ? MIN_PLANNING_SESSION_SLOTS - runLength : 0;
    }

    /**
     * Number of {@code Break} slots that fall within
     * {@link SchedulingConstants#BREAK_EDGE_BUFFER_SLOTS} on-duty slots of the shift's
     * start or end (time-sorted, inclusive of the edge slots themselves). Zero if the
     * teacher didn't work that day.
     */
    private static long edgeBreakSlotCount(List<TeacherSlot> daySlots) {
        List<SlotActivity> sortedActivities = sortedActivitiesOf(daySlots);
        int[] onDutyRange = onDutyRange(sortedActivities);
        if (onDutyRange[0] == -1) {
            return 0;
        }
        long count = 0;
        for (int i = onDutyRange[0]; i <= onDutyRange[1]; i++) {
            boolean tooCloseToEdge = i - onDutyRange[0] < BREAK_EDGE_BUFFER_SLOTS || onDutyRange[1] - i < BREAK_EDGE_BUFFER_SLOTS;
            if (tooCloseToEdge && sortedActivities.get(i) instanceof SlotActivity.Break) {
                count++;
            }
        }
        return count;
    }

    /** Number of maximal runs of consecutive {@code Break} slots in the day (time-sorted). */
    private static long breakPeriodCount(List<TeacherSlot> daySlots) {
        List<SlotActivity> sortedActivities = sortedActivitiesOf(daySlots);
        long periods = 0;
        boolean inBreak = false;
        for (SlotActivity activity : sortedActivities) {
            boolean isBreak = activity instanceof SlotActivity.Break;
            if (isBreak && !inBreak) {
                periods++;
            }
            inBreak = isBreak;
        }
        return periods;
    }

    private static List<SlotActivity> sortedActivitiesOf(List<TeacherSlot> daySlots) {
        return daySlots.stream()
                .sorted(Comparator.comparing(teacherSlot -> teacherSlot.getSlot().start()))
                .map(TeacherSlot::getActivity)
                .toList();
    }

    /** {@code [firstOnDutyIndex, lastOnDutyIndex]} into a time-sorted activity list, or {@code [-1, -1]} if none. */
    private static int[] onDutyRange(List<SlotActivity> sortedActivities) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < sortedActivities.size(); i++) {
            if (isOnDuty(sortedActivities.get(i))) {
                if (first == -1) {
                    first = i;
                }
                last = i;
            }
        }
        return new int[] { first, last };
    }

    /**
     * {@link HardSoftScore} is long-based, but {@link LoadBalance#unfairness()} is a
     * small fractional {@link BigDecimal} (well under 1.0 even for a badly skewed split)
     * — scale it up so imbalance still registers instead of truncating to zero. Kept
     * modest (200, not the 10,000 tried initially): scaling this too high made balance
     * dominate every other soft signal so completely that the solver would refuse moves
     * that reallocated break time into teaching time — even moves that couldn't possibly
     * unbalance early/late (both teachers gain identically) — because they weren't
     * literally the single best available move by this constraint's inflated arithmetic.
     * That starved {@link #weeklyOnDutyBelowTarget} of the search room it needed and
     * capped every teacher's on-duty time well below their weekly target.
     */
    private static final BigDecimal UNFAIRNESS_SCALE = BigDecimal.valueOf(200);

    private static long unfairnessScore(LoadBalance<?> loadBalance) {
        return loadBalance.unfairness().multiply(UNFAIRNESS_SCALE).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static YearWeek weekOf(LocalDate date) {
        return new YearWeek(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private record YearWeek(int year, int week) {
    }
}
