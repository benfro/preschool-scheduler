package net.benfro.scheduler.solver;

import static net.benfro.scheduler.solver.SchedulingConstants.BREAK_EDGE_BUFFER_SLOTS;
import static net.benfro.scheduler.solver.SchedulingConstants.MAX_PLANNING_SESSION_SLOTS;
import static net.benfro.scheduler.solver.SchedulingConstants.MIN_PLANNING_SESSION_SLOTS;
import static net.benfro.scheduler.solver.SchedulingConstants.MIN_WORK_MINUTES_BETWEEN_BREAK_PERIODS;
import static net.benfro.scheduler.solver.SchedulingConstants.SLOT_MINUTES;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.TeacherSlot;

/**
 * Pure analysis of one teacher's activities on one day, time-sorted - every "shape of a
 * shift" fact {@code BreakConstraints} and {@code ShiftShapeConstraints} need (gap count,
 * span, break placement/periods, planning-session length), computed once here and
 * independent of Timefold's constraint-stream machinery. Directly unit-testable without
 * {@code ConstraintVerifier}, unlike the constraints that consume it.
 */
public final class DayShiftAnalysis {

    private final List<SlotActivity> sortedActivities;
    private final int firstOnDutyIndex;
    private final int lastOnDutyIndex;

    private DayShiftAnalysis(List<SlotActivity> sortedActivities) {
        this.sortedActivities = sortedActivities;
        int first = -1;
        int last = -1;
        for (int i = 0; i < sortedActivities.size(); i++) {
            if (SlotActivities.isOnDuty(sortedActivities.get(i))) {
                if (first == -1) {
                    first = i;
                }
                last = i;
            }
        }
        this.firstOnDutyIndex = first;
        this.lastOnDutyIndex = last;
    }

    /** Analyzes one teacher's slots for a single day - callers are responsible for grouping by (teacher, date) first. */
    public static DayShiftAnalysis of(List<TeacherSlot> daySlots) {
        List<SlotActivity> sorted = daySlots.stream()
                .sorted(Comparator.comparing(TeacherSlot::start))
                .map(TeacherSlot::getActivity)
                .toList();
        return new DayShiftAnalysis(sorted);
    }

    private boolean isWorking() {
        return firstOnDutyIndex != -1;
    }

    /**
     * Number of off-duty slots strictly between the first and last on-duty slot of the
     * day. Zero if the teacher didn't work that day, or worked one unbroken block.
     */
    public long gapSlotCount() {
        if (!isWorking()) {
            return 0;
        }
        long gaps = 0;
        for (int i = firstOnDutyIndex; i <= lastOnDutyIndex; i++) {
            if (!SlotActivities.isOnDuty(sortedActivities.get(i))) {
                gaps++;
            }
        }
        return gaps;
    }

    /** Minutes from the first on-duty slot to the last, inclusive. Zero if the teacher didn't work that day. */
    public long shiftSpanMinutes() {
        if (!isWorking()) {
            return 0;
        }
        return (long) (lastOnDutyIndex - firstOnDutyIndex + 1) * SLOT_MINUTES;
    }

    /**
     * How many of the shift's two edges (first on-duty slot, last on-duty slot) are a
     * {@link SlotActivity.Break} — 0, 1, or 2. A single-slot shift that's a break counts
     * once (it's one bad edge, not two). Zero if the teacher didn't work that day.
     */
    public long shiftBreakEdgeCount() {
        if (!isWorking()) {
            return 0;
        }
        long count = 0;
        if (sortedActivities.get(firstOnDutyIndex) instanceof SlotActivity.Break) {
            count++;
        }
        if (lastOnDutyIndex != firstOnDutyIndex && sortedActivities.get(lastOnDutyIndex) instanceof SlotActivity.Break) {
            count++;
        }
        return count;
    }

    /**
     * Number of {@code Break} slots that fall within
     * {@link SchedulingConstants#BREAK_EDGE_BUFFER_SLOTS} on-duty slots of the shift's
     * start or end (inclusive of the edge slots themselves). Zero if the teacher didn't
     * work that day.
     */
    public long edgeBreakSlotCount() {
        if (!isWorking()) {
            return 0;
        }
        long count = 0;
        for (int i = firstOnDutyIndex; i <= lastOnDutyIndex; i++) {
            boolean tooCloseToEdge = i - firstOnDutyIndex < BREAK_EDGE_BUFFER_SLOTS || lastOnDutyIndex - i < BREAK_EDGE_BUFFER_SLOTS;
            if (tooCloseToEdge && sortedActivities.get(i) instanceof SlotActivity.Break) {
                count++;
            }
        }
        return count;
    }

    /** Number of maximal runs of consecutive {@code Break} slots in the day (time-sorted). */
    public long breakPeriodCount() {
        return breakPeriodRanges().size();
    }

    /**
     * Number of {@code Break} slots directly preceded by another {@code Break} slot —
     * zero once every break period is capped at a single 30-minute slot. Equivalent to
     * summing {@code (periodLength - 1)} across every break period in the day.
     */
    public long adjacentBreakSlotCount() {
        long count = 0;
        for (int i = 1; i < sortedActivities.size(); i++) {
            if (sortedActivities.get(i) instanceof SlotActivity.Break && sortedActivities.get(i - 1) instanceof SlotActivity.Break) {
                count++;
            }
        }
        return count;
    }

    /**
     * How many minutes short of {@link SchedulingConstants#MIN_WORK_MINUTES_BETWEEN_BREAK_PERIODS}
     * each gap between two consecutive break periods is, summed across the day — only the
     * on-duty (teaching/planning) time between them counts toward the gap, so a shift
     * that's mid-construction (with stray off-duty slots) isn't given undue credit. Zero
     * if the day has fewer than two break periods.
     */
    public long breakPeriodGapShortfallMinutes() {
        List<int[]> periods = breakPeriodRanges();
        if (periods.size() < 2) {
            return 0;
        }
        long shortfall = 0;
        for (int i = 0; i + 1 < periods.size(); i++) {
            long gapMinutes = 0;
            for (int index = periods.get(i)[1] + 1; index < periods.get(i + 1)[0]; index++) {
                if (SlotActivities.isOnDuty(sortedActivities.get(index))) {
                    gapMinutes += SLOT_MINUTES;
                }
            }
            if (gapMinutes < MIN_WORK_MINUTES_BETWEEN_BREAK_PERIODS) {
                shortfall += MIN_WORK_MINUTES_BETWEEN_BREAK_PERIODS - gapMinutes;
            }
        }
        return shortfall;
    }

    /** Number of maximal runs of consecutive {@code PlanningTime} slots in the day (time-sorted). */
    public long planningSessionCount() {
        long sessions = 0;
        boolean inSession = false;
        for (SlotActivity activity : sortedActivities) {
            boolean isPlanning = activity instanceof SlotActivity.PlanningTime;
            if (isPlanning && !inSession) {
                sessions++;
            }
            inSession = isPlanning;
        }
        return sessions;
    }

    /** Index ranges (inclusive, time-sorted) of each maximal run of consecutive {@code Break} slots. */
    private List<int[]> breakPeriodRanges() {
        List<int[]> ranges = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < sortedActivities.size(); i++) {
            boolean isBreak = sortedActivities.get(i) instanceof SlotActivity.Break;
            if (isBreak && start == -1) {
                start = i;
            } else if (!isBreak && start != -1) {
                ranges.add(new int[] {start, i - 1});
                start = -1;
            }
        }
        if (start != -1) {
            ranges.add(new int[] {start, sortedActivities.size() - 1});
        }
        return ranges;
    }

    /**
     * How many slots short of {@link SchedulingConstants#MIN_PLANNING_SESSION_SLOTS} each
     * too-short {@code PlanningTime} run (maximal run of consecutive planning slots) is,
     * summed across the day. E.g. one lone 1-slot run with a minimum of 2 contributes 1.
     */
    public long shortPlanningSlotCount() {
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
     * How many slots over {@link SchedulingConstants#MAX_PLANNING_SESSION_SLOTS} each
     * too-long {@code PlanningTime} run (maximal run of consecutive planning slots) is,
     * summed across the day. E.g. one 4-slot run with a max of 2 contributes 2.
     */
    public long longPlanningSlotCount() {
        long overflow = 0;
        int runLength = 0;
        for (SlotActivity activity : sortedActivities) {
            if (activity instanceof SlotActivity.PlanningTime) {
                runLength++;
            } else {
                overflow += overflowFor(runLength);
                runLength = 0;
            }
        }
        overflow += overflowFor(runLength);
        return overflow;
    }

    private static long overflowFor(int runLength) {
        return runLength > MAX_PLANNING_SESSION_SLOTS ? runLength - MAX_PLANNING_SESSION_SLOTS : 0;
    }
}
