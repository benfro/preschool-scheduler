package net.benfro.presched.adapter.in.cli;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import ai.timefold.solver.core.api.score.HardSoftScore;

import net.benfro.presched.application.ScheduleResult;
import net.benfro.presched.domain.CoverageRequirement;
import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.Teacher;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.domain.TimeSlot;
import net.benfro.presched.solver.ScheduleGenerator;
import net.benfro.presched.solver.SchedulingConstants;

/**
 * Renders a {@link ScheduleResult} to {@code System.out}: a colored week-view grid per
 * teacher (days across, 30-minute slots down), a coverage-gap check, and a weekly
 * hours/balance summary, via ANSI escape codes. The only place in the application that
 * knows what a "presented" schedule looks like - the use case itself never formats
 * anything, so a JSON presenter for a future REST driving adapter would sit right next to
 * this one, implementing the same shape without touching the application core.
 */
final class ConsoleScheduleGridPresenter {

    private static final String ANSI_RESET = "[0m";
    private static final String ANSI_GREEN = "[32m"; // Teaching
    private static final String ANSI_YELLOW = "[33m"; // Break
    private static final String ANSI_CYAN = "[36m"; // Planning
    private static final String ANSI_DIM = "[90m"; // Off duty

    void present(ScheduleResult result) {
        List<LocalDate> week = result.week();
        List<CoverageRequirement> coverageRequirements = result.coverageRequirements();
        List<Teacher> teachers = result.roster().getTeachers();
        List<TeacherSlot> allSlots = result.roster().getTeacherSlots();
        HardSoftScore score = result.roster().getScore();

        System.out.println(coverageRequirements.size() + " coverage requirements, " + allSlots.size() + " teacher-slots to fill in.");
        System.out.println();
        System.out.println("Score: " + score + (score.isFeasible() ? "  -> FEASIBLE" : "  -> INFEASIBLE"));
        System.out.println("Legend: " + ANSI_GREEN + "T" + ANSI_RESET + "=teaching  " + ANSI_YELLOW + "Br" + ANSI_RESET + "=break  "
                + ANSI_CYAN + "Pl" + ANSI_RESET + "=planning time  " + ANSI_DIM + "." + ANSI_RESET + "=off duty");
        System.out.println();

        printWeekGrids(teachers, week, allSlots);
        printCoverageCheck(coverageRequirements, allSlots);
        printWeeklySummary(teachers, allSlots);
    }

    /** One grid per teacher: days across (X-axis), 30-minute slots down (Y-axis). */
    private static void printWeekGrids(List<Teacher> teachers, List<LocalDate> week, List<TeacherSlot> allSlots) {
        List<TimeSlot> gridSlots = ScheduleGenerator.dailySlots(week.get(0)); // only the times are used
        for (Teacher teacher : teachers) {
            System.out.println("==== " + teacher.name() + " (days →, time ↓) ====");
            StringBuilder header = new StringBuilder(String.format("%-7s", "Time"));
            for (LocalDate date : week) {
                header.append("| ").append(String.format("%-4s", date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)));
            }
            System.out.println(header);
            System.out.println("-".repeat(header.length()));

            for (TimeSlot gridSlot : gridSlots) {
                StringBuilder row = new StringBuilder(String.format("%-7s", gridSlot.start()));
                for (LocalDate date : week) {
                    SlotActivity activity = activityAt(allSlots, teacher, date, gridSlot.start());
                    row.append("| ").append(ansiColor(activity)).append(String.format("%-4s", shortCode(activity))).append(ANSI_RESET);
                }
                System.out.println(row);
            }
            System.out.println();
        }
    }

    private static SlotActivity activityAt(List<TeacherSlot> allSlots, Teacher teacher, LocalDate date, LocalTime start) {
        return allSlots.stream()
                .filter(teacherSlot -> teacherSlot.getTeacher().equals(teacher)
                        && teacherSlot.getSlot().date().equals(date)
                        && teacherSlot.getSlot().start().equals(start))
                .map(TeacherSlot::getActivity)
                .findFirst().orElse(null);
    }

    /**
     * Exhaustive over every {@link SlotActivity} variant (plus {@code null}, for a grid
     * cell with no matching {@link TeacherSlot}) rather than falling through to a default
     * - adding a 5th variant is a compile error here, not a silent mis-render.
     */
    private static String shortCode(SlotActivity activity) {
        if (activity == null) {
            return ".";
        }
        return switch (activity) {
            case SlotActivity.OffDuty _ -> ".";
            case SlotActivity.Teaching _ -> "T";
            case SlotActivity.Break _ -> "Br";
            case SlotActivity.PlanningTime _ -> "Pl";
        };
    }

    /** Exhaustive for the same reason as {@link #shortCode} - see its javadoc. */
    private static String ansiColor(SlotActivity activity) {
        if (activity == null) {
            return ANSI_DIM;
        }
        return switch (activity) {
            case SlotActivity.OffDuty _ -> ANSI_DIM;
            case SlotActivity.Teaching _ -> ANSI_GREEN;
            case SlotActivity.Break _ -> ANSI_YELLOW;
            case SlotActivity.PlanningTime _ -> ANSI_CYAN;
        };
    }

    /** The grids above drop the per-cell coverage column, so verify it here instead and report any gaps. */
    private static void printCoverageCheck(List<CoverageRequirement> coverageRequirements, List<TeacherSlot> allSlots) {
        List<String> gaps = new ArrayList<>();
        for (CoverageRequirement requirement : coverageRequirements) {
            boolean covered = allSlots.stream().anyMatch(teacherSlot -> teacherSlot.getSlot().equals(requirement.slot())
                    && teacherSlot.getActivity() instanceof SlotActivity.Teaching teaching
                    && teaching.group().equals(requirement.group()));
            if (!covered) {
                gaps.add(requirement.slot().date() + " " + requirement.slot().start());
            }
        }
        if (gaps.isEmpty()) {
            System.out.println("Coverage check: all " + coverageRequirements.size() + " required slots covered.");
        } else {
            System.out.println("Coverage check: " + gaps.size() + " GAP(S):");
            gaps.forEach(gap -> System.out.println("  !! " + gap));
        }
        System.out.println();
    }

    private static void printWeeklySummary(List<Teacher> teachers, List<TeacherSlot> allSlots) {
        System.out.println("==== Weekly summary ====");
        System.out.printf("%-8s | %9s | %9s | %9s | %9s | %6s%n", "Teacher", "Teaching", "Break", "Planning", "On-duty", "Cap");
        for (Teacher teacher : teachers) {
            List<SlotActivity> activities = allSlots.stream()
                    .filter(teacherSlot -> teacherSlot.getTeacher().equals(teacher) && teacherSlot.getActivity() != null)
                    .map(TeacherSlot::getActivity)
                    .toList();
            long teachingSlots = activities.stream().filter(activity -> activity instanceof SlotActivity.Teaching).count();
            long breakSlots = activities.stream().filter(activity -> activity instanceof SlotActivity.Break).count();
            long planningSlots = activities.stream().filter(activity -> activity instanceof SlotActivity.PlanningTime).count();
            double onDutyHours = hours(teachingSlots + planningSlots);
            System.out.printf("%-8s | %7.1fh | %7.1fh | %7.1fh | %7.1fh | %5dh%n",
                    teacher.name(), hours(teachingSlots), hours(breakSlots), hours(planningSlots), onDutyHours, teacher.hoursPerWeek());
        }
        System.out.println();

        System.out.println("==== Early/late teaching-slot balance (split at " + SchedulingConstants.MIDDAY + ") ====");
        for (Teacher teacher : teachers) {
            long early = countTeaching(allSlots, teacher, slot -> slot.start().isBefore(SchedulingConstants.MIDDAY));
            long late = countTeaching(allSlots, teacher, slot -> !slot.start().isBefore(SchedulingConstants.MIDDAY));
            System.out.printf("%-8s : %3d early slots (%4.1fh)  /  %3d late slots (%4.1fh)%n",
                    teacher.name(), early, hours(early), late, hours(late));
        }
    }

    private static long countTeaching(List<TeacherSlot> allSlots, Teacher teacher, Predicate<TimeSlot> slotFilter) {
        return allSlots.stream()
                .filter(teacherSlot -> teacherSlot.getTeacher().equals(teacher)
                        && teacherSlot.getActivity() instanceof SlotActivity.Teaching
                        && slotFilter.test(teacherSlot.getSlot()))
                .count();
    }

    private static double hours(long slotCount) {
        return slotCount * SchedulingConstants.SLOT_MINUTES / 60.0;
    }
}
