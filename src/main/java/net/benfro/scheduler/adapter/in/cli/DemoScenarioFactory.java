package net.benfro.scheduler.adapter.in.cli;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.benfro.scheduler.application.ScheduleRequest;
import net.benfro.scheduler.domain.Group;
import net.benfro.scheduler.domain.Pupil;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TimeSlot;
import net.benfro.scheduler.solver.ScheduleGenerator;
import net.benfro.scheduler.solver.SchedulingConstants;

/**
 * Demo-only synthetic scenario generation - randomized pupil attendance, a fixed Mon-Fri
 * week, one group shared by a fixed set of teachers. Lives in the CLI adapter, not the
 * application core: a real driving adapter (e.g. a future REST endpoint) would assemble a
 * {@link ScheduleRequest} from actual stored data instead of random numbers.
 */
final class DemoScenarioFactory {

    private static final int MIN_DAILY_ATTENDANCE_HOURS = 5;
    private static final int MAX_DAILY_ATTENDANCE_HOURS = 9;

    private DemoScenarioFactory() {
    }

    /** One group, {@code pupilCount} pupils with randomized 5-9h/day attendance, teaching the Mon-Fri week containing {@code anyDate}. */
    static ScheduleRequest weeklyScenario(String groupName, List<String> teacherNames, int pupilCount, LocalDate anyDate, long randomSeed) {
        Random random = new Random(randomSeed);
        List<LocalDate> week = weekdays(anyDate);
        Group group = buildGroup(groupName, pupilCount, week, random);
        List<Teacher> teachers = teacherNames.stream().map(name -> new Teacher(name, group)).toList();
        return new ScheduleRequest(teachers, group, week);
    }

    /** Monday-Friday of the week containing {@code anyDate} - the preschool is closed weekends. */
    private static List<LocalDate> weekdays(LocalDate anyDate) {
        LocalDate monday = anyDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return ScheduleGenerator.dateRange(monday, monday.plusDays(4));
    }

    private static Group buildGroup(String name, int pupilCount, List<LocalDate> week, Random random) {
        Group group = new Group(name, new ArrayList<>());
        for (int i = 0; i < pupilCount; i++) {
            Pupil pupil = new Pupil(new ArrayList<>());
            for (LocalDate date : week) {
                pupil.addStayingTime(randomStayingTime(date, random));
            }
            group.addPupil(pupil);
        }
        return group;
    }

    /** A random staying time of 5-9h that day, aligned to the 30-minute grid and clipped to opening hours. */
    private static TimeSlot randomStayingTime(LocalDate date, Random random) {
        int hours = MIN_DAILY_ATTENDANCE_HOURS
                + random.nextInt(MAX_DAILY_ATTENDANCE_HOURS - MIN_DAILY_ATTENDANCE_HOURS + 1);
        int durationMinutes = hours * SchedulingConstants.MINUTES_PER_HOUR;
        int windowMinutes = (int) Duration.between(SchedulingConstants.OPENING_TIME, SchedulingConstants.CLOSING_TIME).toMinutes();
        int slackSlots = (windowMinutes - durationMinutes) / SchedulingConstants.SLOT_MINUTES;
        int startSlot = slackSlots > 0 ? random.nextInt(slackSlots + 1) : 0;
        LocalTime start = SchedulingConstants.OPENING_TIME.plusMinutes((long) startSlot * SchedulingConstants.SLOT_MINUTES);
        return new TimeSlot(date, start, start.plusMinutes(durationMinutes));
    }
}
