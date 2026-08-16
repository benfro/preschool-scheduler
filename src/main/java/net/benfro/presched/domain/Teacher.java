package net.benfro.presched.domain;

/**
 * A staff member. {@code group} is the teacher's home/primary group — not a hard
 * assignment, just a fact used by {@code preferHomeGroupAssignment} to favor scheduling
 * a teacher with their own group over other groups when the solver has a choice.
 * Actual day-to-day coverage is decided per {@link TeacherSlot}, since two or more
 * teachers may cover the same group and one teacher may cover several groups.
 */
public record Teacher(String name, Group group, int hoursPerWeek, int dailyHoursMax) {

    public static final int DEFAULT_HOURS_PER_WEEK = 40;
    public static final int DEFAULT_DAILY_HOURS_MAX = 8;

    public Teacher(String name, Group group) {
        this(name, group, DEFAULT_HOURS_PER_WEEK, DEFAULT_DAILY_HOURS_MAX);
    }
}
