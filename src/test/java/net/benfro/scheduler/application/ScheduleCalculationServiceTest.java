package net.benfro.scheduler.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.HardSoftScore;

import net.benfro.scheduler.application.port.out.SolveSchedulePort;
import net.benfro.scheduler.domain.CoverageRequirement;
import net.benfro.scheduler.domain.Group;
import net.benfro.scheduler.domain.Pupil;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TeacherRoster;
import net.benfro.scheduler.domain.TimeSlot;
import net.benfro.scheduler.solver.ScheduleGenerator;

/**
 * Unit tests for {@link ScheduleCalculationService} against a stub {@link SolveSchedulePort}
 * - no real Timefold solve involved, unlike {@code SolveSmokeTest}. These exercise the
 * service's own responsibility only: assembling the unsolved problem correctly from a
 * {@link ScheduleRequest} and returning whatever the port hands back, untouched.
 */
class ScheduleCalculationServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, Month.AUGUST, 17); // a Monday

    @Test
    void buildsAnUnsolvedProblemMatchingTheRequest() {
        Group group = new Group("Ducklings", new ArrayList<>());
        Pupil pupil = new Pupil(new ArrayList<>());
        pupil.addStayingTime(new TimeSlot(DATE, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        group.addPupil(pupil);
        Teacher alice = new Teacher("Alice", group);
        ScheduleRequest request = new ScheduleRequest(List.of(alice), group, List.of(DATE));

        CapturingSolveSchedulePort solveSchedulePort = new CapturingSolveSchedulePort();
        ScheduleCalculationService service = new ScheduleCalculationService(solveSchedulePort);

        service.calculate(request);

        TeacherRoster problem = solveSchedulePort.capturedProblem;
        assertEquals(List.of(alice), problem.getTeachers());
        assertEquals(List.of(group), problem.getGroups());
        assertEquals(ScheduleGenerator.coverageRequirements(group, DATE).size(), problem.getCoverageRequirements().size());
        assertEquals(ScheduleGenerator.teacherSlots(alice, DATE).size(), problem.getTeacherSlots().size());
        assertNull(problem.getScore(), "the problem handed to the port must be unsolved");
    }

    @Test
    void returnsExactlyWhatThePortSolves() {
        Group group = new Group("Ducklings", new ArrayList<>());
        Teacher alice = new Teacher("Alice", group);
        ScheduleRequest request = new ScheduleRequest(List.of(alice), group, List.of(DATE));

        TeacherRoster solvedRoster = new TeacherRoster(List.of(alice), List.of(group), List.of(), List.of(), HardSoftScore.of(0, 5));
        SolveSchedulePort solveSchedulePort = problem -> solvedRoster;
        ScheduleCalculationService service = new ScheduleCalculationService(solveSchedulePort);

        ScheduleResult result = service.calculate(request);

        assertSame(solvedRoster, result.roster(), "the service must not build its own result - only relay the port's");
        assertEquals(List.of(DATE), result.week());
    }

    @Test
    void resultCoverageRequirementsMatchWhatWasSolvedAgainst() {
        Group group = new Group("Ducklings", new ArrayList<>());
        Pupil pupil = new Pupil(new ArrayList<>());
        pupil.addStayingTime(new TimeSlot(DATE, LocalTime.of(9, 0), LocalTime.of(9, 30)));
        group.addPupil(pupil);
        Teacher alice = new Teacher("Alice", group);
        ScheduleRequest request = new ScheduleRequest(List.of(alice), group, List.of(DATE));

        ScheduleCalculationService service = new ScheduleCalculationService(problem -> problem); // "solves" to itself, unchanged

        ScheduleResult result = service.calculate(request);

        List<CoverageRequirement> expected = ScheduleGenerator.coverageRequirements(group, DATE);
        assertEquals(expected, result.coverageRequirements());
    }

    @Test
    void solveSchedulePortIsInvokedExactlyOnce() {
        Group group = new Group("Ducklings", new ArrayList<>());
        Teacher alice = new Teacher("Alice", group);
        ScheduleRequest request = new ScheduleRequest(List.of(alice), group, List.of(DATE));

        int[] invocationCount = { 0 };
        SolveSchedulePort solveSchedulePort = problem -> {
            invocationCount[0]++;
            return problem;
        };
        ScheduleCalculationService service = new ScheduleCalculationService(solveSchedulePort);

        service.calculate(request);

        assertEquals(1, invocationCount[0]);
    }

    private static final class CapturingSolveSchedulePort implements SolveSchedulePort {
        private TeacherRoster capturedProblem;

        @Override
        public TeacherRoster solve(TeacherRoster problem) {
            this.capturedProblem = problem;
            return problem;
        }
    }
}
