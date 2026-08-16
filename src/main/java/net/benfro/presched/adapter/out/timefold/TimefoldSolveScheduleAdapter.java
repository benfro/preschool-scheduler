package net.benfro.presched.adapter.out.timefold;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import jakarta.enterprise.context.ApplicationScoped;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;

import net.benfro.presched.application.port.out.SolveSchedulePort;
import net.benfro.presched.domain.TeacherRoster;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.solver.TeacherScheduleConstraintProvider;

/**
 * Driven adapter: solves a {@link TeacherRoster} problem with Timefold's standalone
 * {@link SolverFactory} (no Quarkus/CDI required - safe to use from a plain {@code main},
 * and equally usable from a future CDI-managed driving adapter). Every Timefold-specific
 * detail lives here, invisible to
 * {@link net.benfro.presched.application.ScheduleCalculationService}: solver
 * configuration, termination, and the entity-order shuffle below.
 *
 * <p>Construction Heuristic initializes planning entities in list order. Left as
 * {@link net.benfro.presched.solver.ScheduleGenerator} generates them (all of one
 * teacher's slots, then the next teacher's), CH front-loads every coverage requirement
 * onto the first teacher up to their caps and leaves the rest pure overflow - a pure
 * ordering artifact, not a real scheduling preference (see the project's
 * "presched-timefold-gotchas" memory for how this was originally diagnosed). Shuffling
 * the entity list with a fixed seed removes the bias while keeping solves reproducible.
 *
 * <p>{@code @ApplicationScoped} so a CDI-managed driving adapter (e.g. the REST resource)
 * can get one injected as the {@link SolveSchedulePort} implementation, using the no-arg
 * constructor's defaults below; {@link net.benfro.presched.adapter.in.cli.ScheduleDemoCli}
 * still just calls {@code new TimefoldSolveScheduleAdapter()} directly, no CDI required.
 */
@ApplicationScoped
public class TimefoldSolveScheduleAdapter implements SolveSchedulePort {

    private static final long DEFAULT_ENTITY_SHUFFLE_SEED = 42L;
    private static final long DEFAULT_SOLVER_RANDOM_SEED = 43L;
    private static final Duration DEFAULT_TERMINATION_SPENT_LIMIT = Duration.ofSeconds(30);

    private final long entityShuffleSeed;
    private final long solverRandomSeed;
    private final Duration terminationSpentLimit;

    public TimefoldSolveScheduleAdapter() {
        this(DEFAULT_ENTITY_SHUFFLE_SEED, DEFAULT_SOLVER_RANDOM_SEED, DEFAULT_TERMINATION_SPENT_LIMIT);
    }

    public TimefoldSolveScheduleAdapter(long entityShuffleSeed, long solverRandomSeed, Duration terminationSpentLimit) {
        this.entityShuffleSeed = entityShuffleSeed;
        this.solverRandomSeed = solverRandomSeed;
        this.terminationSpentLimit = terminationSpentLimit;
    }

    @Override
    public TeacherRoster solve(TeacherRoster problem) {
        List<TeacherSlot> shuffled = new ArrayList<>(problem.getTeacherSlots());
        Collections.shuffle(shuffled, new Random(entityShuffleSeed));
        problem.setTeacherSlots(shuffled);

        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(TeacherRoster.class)
                .withEntityClasses(TeacherSlot.class)
                .withConstraintProviderClass(TeacherScheduleConstraintProvider.class)
                .withRandomSeed(solverRandomSeed)
                .withTerminationSpentLimit(terminationSpentLimit);

        SolverFactory<TeacherRoster> solverFactory = SolverFactory.create(solverConfig);
        Solver<TeacherRoster> solver = solverFactory.buildSolver();
        return solver.solve(problem);
    }
}
