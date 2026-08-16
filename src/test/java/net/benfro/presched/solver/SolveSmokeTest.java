package net.benfro.presched.solver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;

import net.benfro.presched.domain.CoverageRequirement;
import net.benfro.presched.domain.Group;
import net.benfro.presched.domain.Pupil;
import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.Teacher;
import net.benfro.presched.domain.TeacherRoster;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.domain.TimeSlot;

/**
 * End-to-end smoke test: actually invokes {@link Solver#solve}, not just
 * {@code ConstraintVerifier} (which only checks one constraint's math in isolation and
 * would not have caught the scoring-cliff bug this test guards against — see
 * {@code insufficientWeeklyPlanningTime}'s javadoc).
 */
class SolveSmokeTest {

    @Test
    void trivialProblemReachesAFeasibleSolution() {
        LocalDate date = LocalDate.of(2026, 8, 17);
        Pupil pupil = new Pupil(new ArrayList<>());
        pupil.addStayingTime(new TimeSlot(date, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        Group group = new Group("TestGroup", new ArrayList<>());
        group.addPupil(pupil);

        Teacher teacher = new Teacher("Solo", group);
        List<CoverageRequirement> requirements = ScheduleGenerator.coverageRequirements(group, date);
        List<TeacherSlot> teacherSlots = ScheduleGenerator.teacherSlots(teacher, date);

        TeacherRoster problem = new TeacherRoster(List.of(teacher), List.of(group), requirements, teacherSlots, null);

        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(TeacherRoster.class)
                .withEntityClasses(TeacherSlot.class)
                .withConstraintProviderClass(TeacherScheduleConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(5));
        SolverFactory<TeacherRoster> solverFactory = SolverFactory.create(solverConfig);
        Solver<TeacherRoster> solver = solverFactory.buildSolver();

        TeacherRoster solution = solver.solve(problem);

        assertTrue(solution.getScore().isFeasible(), "expected a feasible solution, got " + solution.getScore());
        for (CoverageRequirement requirement : requirements) {
            boolean covered = solution.getTeacherSlots().stream()
                    .anyMatch(teacherSlot -> teacherSlot.getSlot().equals(requirement.slot())
                            && teacherSlot.getActivity() instanceof SlotActivity.Teaching teaching
                            && teaching.group().equals(requirement.group()));
            assertTrue(covered, "expected " + requirement.slot() + " to be covered");
        }
    }
}
