package net.benfro.scheduler.solver;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.TeacherSlot;

/**
 * Cross-day analysis of one teacher's planning-session spacing across a week - whether two
 * planning sessions land on calendar-adjacent workdays with no working day between them.
 * Complements {@link DayShiftAnalysis}, which only looks within a single day.
 *
 * <p>"Workday" here means any date the teacher has a {@link TeacherSlot} for at all -
 * {@code ScheduleGenerator.teacherSlots} generates one for every date in the schedule's
 * week regardless of whether that day is worked, so a teacher's own distinct slot dates
 * are exactly the schedule's opening days, not just the ones they happen to work.
 */
public final class WeekPlanningAnalysis {

    private WeekPlanningAnalysis() {
    }

    /**
     * Number of pairs of calendar-adjacent workdays (consecutive entries in the teacher's
     * own sorted, distinct slot dates) that both contain a planning session — i.e., no
     * workday gap separates them. {@code teacherSlots} need not already be grouped by
     * teacher; callers are expected to have grouped by teacher first (mixing multiple
     * teachers' slots in would conflate their calendars).
     */
    public static long adjacentPlanningDayCount(List<TeacherSlot> teacherSlots) {
        Set<LocalDate> planningDays = teacherSlots.stream()
                .filter(teacherSlot -> teacherSlot.getActivity() instanceof SlotActivity.PlanningTime)
                .map(TeacherSlot::date)
                .collect(Collectors.toCollection(HashSet::new));
        if (planningDays.isEmpty()) {
            return 0;
        }

        List<LocalDate> workdays = teacherSlots.stream()
                .map(TeacherSlot::date)
                .distinct()
                .sorted()
                .toList();

        long violations = 0;
        for (int i = 0; i + 1 < workdays.size(); i++) {
            if (planningDays.contains(workdays.get(i)) && planningDays.contains(workdays.get(i + 1))) {
                violations++;
            }
        }
        return violations;
    }
}
