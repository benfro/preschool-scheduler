package net.benfro.scheduler.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.benfro.scheduler.domain.AttendanceWindow;
import net.benfro.scheduler.domain.CoverageRequirement;
import net.benfro.scheduler.domain.Group;
import net.benfro.scheduler.domain.Pupil;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.domain.TimeSlot;

class ScheduleGeneratorTest {

    private static final LocalDate DATE = LocalDate.of(2026, Month.AUGUST, 17);

    @Test
    void dailySlotsSpanOpeningHoursInHalfHourBlocks() {
        List<TimeSlot> slots = ScheduleGenerator.dailySlots(DATE);

        assertEquals(21, slots.size());
        assertEquals(new TimeSlot(DATE, LocalTime.of(7, 0), LocalTime.of(7, 30)), slots.getFirst());
        assertEquals(new TimeSlot(DATE, LocalTime.of(17, 0), LocalTime.of(17, 30)), slots.getLast());
    }

    @Test
    void teacherSlotsAreGeneratedUnassignedForEveryGridSlot() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = ScheduleGenerator.teacherSlots(teacher, DATE);

        assertEquals(21, slots.size());
        for (TeacherSlot slot : slots) {
            assertEquals(teacher, slot.getTeacher());
            assertNull(slot.getActivity());
        }
        assertEquals(21, slots.stream().map(TeacherSlot::getId).distinct().count());
    }

    @Test
    void teacherSlotsAreGeneratedForEachRequestedDate() {
        Teacher teacher = new Teacher("Alice", null);
        List<LocalDate> dates = ScheduleGenerator.dateRange(DATE, DATE.plusDays(1));

        List<TeacherSlot> slots = ScheduleGenerator.teacherSlots(teacher, dates);

        assertEquals(42, slots.size());
    }

    @Test
    void coverageRequirementsCoverOnlyThePupilPresenceWindow() {
        Pupil pupil = new Pupil(List.of());
        pupil.addStayingTime(new AttendanceWindow(DATE, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        Group group = new Group("Group", List.of());
        group.addPupil(pupil);

        List<CoverageRequirement> requirements = ScheduleGenerator.coverageRequirements(group, DATE);

        assertEquals(6, requirements.size());
        assertEquals(new TimeSlot(DATE, LocalTime.of(9, 0), LocalTime.of(9, 30)), requirements.getFirst().slot());
        assertEquals(new TimeSlot(DATE, LocalTime.of(11, 30), LocalTime.of(12, 0)), requirements.getLast().slot());
        requirements.forEach(requirement -> assertEquals(group, requirement.group()));
    }

    @Test
    void coverageRequirementsAreClippedToOpeningHours() {
        Pupil pupil = new Pupil(List.of());
        pupil.addStayingTime(new AttendanceWindow(DATE, LocalTime.of(6, 30), LocalTime.of(8, 0)));
        Group group = new Group("Group", List.of());
        group.addPupil(pupil);

        List<CoverageRequirement> requirements = ScheduleGenerator.coverageRequirements(group, DATE);

        assertEquals(2, requirements.size());
        assertEquals(LocalTime.of(7, 0), requirements.getFirst().slot().start());
    }

    @Test
    void overlappingPupilTimesDoNotDuplicateRequirements() {
        Pupil earlyPupil = new Pupil(List.of());
        earlyPupil.addStayingTime(new AttendanceWindow(DATE, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        Pupil latePupil = new Pupil(List.of());
        latePupil.addStayingTime(new AttendanceWindow(DATE, LocalTime.of(10, 0), LocalTime.of(13, 0)));
        Group group = new Group("Group", List.of());
        group.addPupil(earlyPupil);
        group.addPupil(latePupil);

        List<CoverageRequirement> requirements = ScheduleGenerator.coverageRequirements(group, DATE);

        // union of 09:00-11:00 and 10:00-13:00 is 09:00-13:00 -> 8 half-hour slots, no duplicates
        assertEquals(8, requirements.size());
        assertEquals(8, requirements.stream().map(CoverageRequirement::slot).distinct().count());
    }

    @Test
    void groupWithNoPupilPresentThatDayHasNoCoverageRequirement() {
        Group group = new Group("Group", List.of());
        assertTrue(ScheduleGenerator.coverageRequirements(group, DATE).isEmpty());
    }

    @Test
    void pupilPresenceOnOtherDatesIsIgnored() {
        Pupil pupil = new Pupil(List.of());
        pupil.addStayingTime(new AttendanceWindow(DATE.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0)));
        Group group = new Group("Group", List.of());
        group.addPupil(pupil);

        assertTrue(ScheduleGenerator.coverageRequirements(group, DATE).isEmpty());
    }

    @Test
    void dateRangeIsInclusiveOfBothEnds() {
        List<LocalDate> dates = ScheduleGenerator.dateRange(DATE, DATE.plusDays(2));

        assertEquals(List.of(DATE, DATE.plusDays(1), DATE.plusDays(2)), dates);
    }
}
