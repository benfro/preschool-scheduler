package net.benfro.presched.application.port.out;

import net.benfro.presched.domain.TeacherRoster;

/**
 * Outbound port: hands an unsolved {@link TeacherRoster} problem (every
 * {@code TeacherSlot.activity} still {@code null}) to whichever solving engine is wired
 * in, and gets back a solved one.
 * {@link net.benfro.presched.application.ScheduleCalculationService} depends only on this
 * interface, never on a Timefold {@code SolverFactory} directly - the driven adapter
 * (currently {@link net.benfro.presched.adapter.out.timefold.TimefoldSolveScheduleAdapter})
 * owns every solver-specific concern, including the construction-heuristic entity-order
 * workaround documented on that adapter.
 */
public interface SolveSchedulePort {

    TeacherRoster solve(TeacherRoster problem);
}
