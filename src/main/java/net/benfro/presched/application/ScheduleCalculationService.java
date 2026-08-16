package net.benfro.presched.application;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import net.benfro.presched.application.port.in.CalculateScheduleUseCase;
import net.benfro.presched.application.port.out.SolveSchedulePort;
import net.benfro.presched.domain.CoverageRequirement;
import net.benfro.presched.domain.Teacher;
import net.benfro.presched.domain.TeacherRoster;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.solver.ScheduleGenerator;

/**
 * The use case itself: assembles the unsolved {@link TeacherRoster} problem from a
 * {@link ScheduleRequest} (coverage requirements plus one blank {@link TeacherSlot} per
 * teacher per grid slot, via {@link ScheduleGenerator}), delegates the actual solving to
 * the injected {@link SolveSchedulePort}, and wraps the result. Pure application logic -
 * no Timefold {@code SolverFactory}, no console/ANSI formatting, nothing adapter-specific.
 *
 * <p>Only a single {@link net.benfro.presched.domain.Group} per request is supported for
 * now, matching every scenario built so far; widening {@link ScheduleRequest} to carry
 * several groups would only require changing the {@code List.of(...)} below.
 *
 * <p>{@code @ApplicationScoped} so a CDI-managed driving adapter (e.g. the REST resource)
 * can simply {@code @Inject} a {@link CalculateScheduleUseCase} - but the class stays a
 * plain constructor-injected POJO underneath, so {@link net.benfro.presched.adapter.in.cli.ScheduleDemoCli}
 * can keep wiring it with a bare {@code new}, no CDI container required.
 */
@ApplicationScoped
public class ScheduleCalculationService implements CalculateScheduleUseCase {

    private final SolveSchedulePort solveSchedulePort;

    @Inject
    public ScheduleCalculationService(SolveSchedulePort solveSchedulePort) {
        this.solveSchedulePort = solveSchedulePort;
    }

    @Override
    public ScheduleResult calculate(ScheduleRequest request) {
        List<CoverageRequirement> coverageRequirements =
                ScheduleGenerator.coverageRequirements(request.group(), request.week());

        List<TeacherSlot> teacherSlots = new ArrayList<>();
        for (Teacher teacher : request.teachers()) {
            teacherSlots.addAll(ScheduleGenerator.teacherSlots(teacher, request.week()));
        }

        TeacherRoster problem =
                new TeacherRoster(request.teachers(), List.of(request.group()), coverageRequirements, teacherSlots, null);
        TeacherRoster solved = solveSchedulePort.solve(problem);

        return new ScheduleResult(request.week(), coverageRequirements, solved);
    }
}
