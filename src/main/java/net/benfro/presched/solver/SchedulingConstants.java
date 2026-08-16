package net.benfro.presched.solver;

import java.time.Duration;
import java.time.LocalTime;

import net.benfro.presched.domain.Teacher;

/** Shared timing constants for schedule generation and constraint evaluation. */
public final class SchedulingConstants {

    private SchedulingConstants() {
    }

    /** The preschool is staffed from 07:00... */
    public static final LocalTime OPENING_TIME = LocalTime.of(7, 0);
    /** ...to 17:30. */
    public static final LocalTime CLOSING_TIME = LocalTime.of(17, 30);

    /** Granularity of the planning grid — fine enough to place breaks and planning time. */
    public static final int SLOT_MINUTES = 30;

    public static final int MINUTES_PER_HOUR = 60;

    /** A teacher on duty this long in one day must have a break somewhere in it. */
    public static final int BREAK_REQUIRED_AFTER_MINUTES = 6 * MINUTES_PER_HOUR;

    /**
     * A working day has at most this many distinct break periods (maximal runs of
     * consecutive {@code Break} slots) — e.g. one mid-morning break plus one lunch break,
     * not breaks scattered across five or six separate moments.
     */
    public static final int MAX_BREAK_PERIODS_PER_DAY = 2;

    /**
     * How many half-hour slots one teacher can plausibly teach in a day, based on the
     * default daily cap. The 07:00-17:30 window (10.5h) exceeds this, so any group whose
     * pupils are present all day structurally needs more than one teacher — that's
     * expected, not fragmentation. Used as the yardstick for how many distinct teachers
     * a group's day *should* need before {@code minimizeDistinctTeachersPerGroupPerDay}
     * treats extra teachers as avoidable churn.
     */
    public static final int MAX_TEACHING_SLOTS_PER_TEACHER_PER_DAY =
            Teacher.DEFAULT_DAILY_HOURS_MAX * MINUTES_PER_HOUR / SLOT_MINUTES;

    /** Every teacher who works at all in a week should get at least this much planning time. */
    public static final int WEEKLY_PLANNING_TIME_TARGET_MINUTES = 2 * MINUTES_PER_HOUR;

    /**
     * How much more heavily a minute of planning-time deviation is weighted than a minute
     * of on-duty shortfall from {@code weeklyOnDutyBelowTarget}. Without this, the two
     * constraints cancel out at 1:1 — every extra planning slot beyond the 2h target
     * relieves exactly as much on-duty-shortfall penalty as it costs in planning-off-target
     * penalty, a wash that let planning time drift to hours instead of the intended 2h.
     * Weighting it higher makes overshooting the planning target strictly more expensive
     * than the on-duty benefit it buys, so the solver reaches for more teaching instead.
     */
    public static final int PLANNING_TARGET_WEIGHT = 5;

    /**
     * Extra clock-in-to-clock-out span, beyond a teacher's counted (teaching + planning)
     * daily cap, allowed for breaks. Without this, nothing stops a technically-gap-free
     * shift from stretching across the entire opening window padded with unlimited break
     * time — trivially "continuous" but not a real shift.
     */
    public static final int MAX_BREAK_ALLOWANCE_MINUTES = MINUTES_PER_HOUR;

    /**
     * A {@code PlanningTime} session (a maximal run of consecutive planning slots in a
     * day) must be at least this many slots long — a lone isolated half-hour of planning
     * time is not a usable session.
     */
    public static final int MIN_PLANNING_SESSION_SLOTS = 2;

    /**
     * A {@code Break} may not fall within this many on-duty slots of a teacher's shift
     * start, nor within this many on-duty slots of the shift's end — breaks must sit at
     * least this far into the shift and this far before it finishes.
     */
    public static final int BREAK_EDGE_BUFFER_SLOTS = 4;

    /** Midpoint of the opening window — the boundary between "early" and "late" slots. */
    public static final LocalTime MIDDAY = OPENING_TIME.plusMinutes(
            Duration.between(OPENING_TIME, CLOSING_TIME).toMinutes() / 2);
}
