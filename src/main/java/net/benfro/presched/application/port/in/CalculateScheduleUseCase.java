package net.benfro.presched.application.port.in;

import net.benfro.presched.application.ScheduleRequest;
import net.benfro.presched.application.ScheduleResult;

/**
 * Inbound port: what a driving adapter (the CLI demo today, a REST resource tomorrow)
 * calls to have a weekly teacher schedule calculated. The only entry point into the
 * application core - driving adapters never talk to
 * {@link net.benfro.presched.solver.ScheduleGenerator} or a solver directly.
 */
public interface CalculateScheduleUseCase {

    ScheduleResult calculate(ScheduleRequest request);
}
