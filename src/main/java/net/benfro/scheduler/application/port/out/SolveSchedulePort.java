package net.benfro.scheduler.application.port.out;

import net.benfro.scheduler.domain.TeacherRoster;

/**
 * Outbound port: hands an unsolved {@link TeacherRoster} problem (every
 * {@code TeacherSlot.activity} still {@code null}) to whichever solving engine is wired
 * in, and gets back a solved one.
 * {@link net.benfro.scheduler.application.ScheduleCalculationService} depends only on this
 * interface, never on a Timefold {@code SolverFactory} directly - the driven adapter
 * (currently {@link net.benfro.scheduler.adapter.out.timefold.TimefoldSolveScheduleAdapter})
 * owns every solver-specific concern, including the construction-heuristic entity-order
 * workaround documented on that adapter.
 */
public interface SolveSchedulePort {

    TeacherRoster solve(TeacherRoster problem);
}
