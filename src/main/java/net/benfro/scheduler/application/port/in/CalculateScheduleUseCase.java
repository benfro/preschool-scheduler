package net.benfro.scheduler.application.port.in;

import net.benfro.scheduler.application.ScheduleRequest;
import net.benfro.scheduler.application.ScheduleResult;

/**
 * Inbound port: what a driving adapter (the CLI demo today, a REST resource tomorrow)
 * calls to have a weekly teacher schedule calculated. The only entry point into the
 * application core - driving adapters never talk to
 * {@link net.benfro.scheduler.solver.ScheduleGenerator} or a solver directly.
 */
public interface CalculateScheduleUseCase {

    ScheduleResult calculate(ScheduleRequest request);
}
