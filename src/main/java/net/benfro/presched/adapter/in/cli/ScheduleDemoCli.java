package net.benfro.presched.adapter.in.cli;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import net.benfro.presched.adapter.out.timefold.TimefoldSolveScheduleAdapter;
import net.benfro.presched.application.ScheduleCalculationService;
import net.benfro.presched.application.ScheduleRequest;
import net.benfro.presched.application.ScheduleResult;
import net.benfro.presched.application.port.in.CalculateScheduleUseCase;
import net.benfro.presched.domain.Teacher;

/**
 * Driving adapter: builds a demo {@link ScheduleRequest} (one group, two teachers, nine
 * pupils with randomized daily attendance), calls the {@link CalculateScheduleUseCase}
 * (wired here to the Timefold driven adapter - this class is the composition root), and
 * hands the {@link ScheduleResult} to {@link ConsoleScheduleGridPresenter}. No
 * Quarkus/CDI boot required - run as a plain {@code main}.
 *
 * <p>A REST driving adapter would look structurally identical: build a
 * {@code ScheduleRequest} from the HTTP payload, call the very same use case, hand the
 * result to a JSON presenter instead of this console one - neither
 * {@link net.benfro.presched.application.ScheduleCalculationService} nor
 * {@link TimefoldSolveScheduleAdapter} would need to change.
 */
public final class ScheduleDemoCli {

    private static final long SCENARIO_RANDOM_SEED = 42L;
    private static final int PUPIL_COUNT = 9;

    public static void main(String[] args) {
        ScheduleRequest request = DemoScenarioFactory.weeklyScenario(
                "BeerCans", List.of("Alice", "John"), PUPIL_COUNT, LocalDate.now(), SCENARIO_RANDOM_SEED);

        System.out.println("Group '" + request.group().name() + "': " + PUPIL_COUNT + " pupils, " + request.teachers().size()
                + " teachers (" + request.teachers().stream().map(Teacher::name).collect(Collectors.joining(", "))
                + "), week of " + request.week().get(0) + " to " + request.week().get(request.week().size() - 1));
        System.out.println("Solving...");

        CalculateScheduleUseCase calculateSchedule = new ScheduleCalculationService(new TimefoldSolveScheduleAdapter());
        ScheduleResult result = calculateSchedule.calculate(request);

        new ConsoleScheduleGridPresenter().present(result);
    }

    private ScheduleDemoCli() {
    }
}
