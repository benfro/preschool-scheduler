package net.benfro.scheduler.solver;

import java.util.stream.Stream;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

import net.benfro.scheduler.solver.constraint.BreakConstraints;
import net.benfro.scheduler.solver.constraint.CoverageConstraints;
import net.benfro.scheduler.solver.constraint.FairnessConstraints;
import net.benfro.scheduler.solver.constraint.PreferenceConstraints;
import net.benfro.scheduler.solver.constraint.ShiftShapeConstraints;
import net.benfro.scheduler.solver.constraint.WorkingHoursConstraints;

/**
 * Composition root for every constraint in the schedule-calculation problem. Timefold's
 * {@code SolverConfig.withConstraintProviderClass} needs exactly one
 * {@link ConstraintProvider} class, so this stays that single entry point - but none of the
 * 21 constraints are defined inline here anymore. They live in cohesive groups under
 * {@link net.benfro.scheduler.solver.constraint}:
 * <ul>
 *   <li>{@link CoverageConstraints} - every {@code CoverageRequirement} must actually be taught</li>
 *   <li>{@link WorkingHoursConstraints} - daily/weekly hour caps</li>
 *   <li>{@link BreakConstraints} - whether/how often/where breaks may fall</li>
 *   <li>{@link ShiftShapeConstraints} - contiguity, span, planning-session length</li>
 *   <li>{@link FairnessConstraints} - even early/late spread across teachers</li>
 *   <li>{@link PreferenceConstraints} - home-group, weekly targets, teacher-handoff avoidance</li>
 * </ul>
 * {@link DayShiftAnalysis}, {@link IsoWeek} and {@link SlotActivities} hold the analysis
 * logic shared across more than one group - see their own javadoc.
 */
public class TeacherScheduleConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return Stream.of(
                        CoverageConstraints.define(constraintFactory),
                        WorkingHoursConstraints.define(constraintFactory),
                        BreakConstraints.define(constraintFactory),
                        ShiftShapeConstraints.define(constraintFactory),
                        FairnessConstraints.define(constraintFactory),
                        PreferenceConstraints.define(constraintFactory))
                .flatMap(Stream::of)
                .toArray(Constraint[]::new);
    }
}
