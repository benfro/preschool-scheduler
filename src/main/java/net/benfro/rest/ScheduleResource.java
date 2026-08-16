package net.benfro.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import net.benfro.scheduler.application.ScheduleRequest;
import net.benfro.scheduler.application.ScheduleResult;
import net.benfro.scheduler.application.port.in.CalculateScheduleUseCase;

/**
 * Driving adapter: the REST seam the ports-and-adapters refactor was built for.
 * Structurally identical to {@link net.benfro.scheduler.adapter.in.cli.ScheduleDemoCli} -
 * take a request, call {@link CalculateScheduleUseCase}, hand back the result - just over
 * HTTP/JSON instead of an ANSI console grid. Neither
 * {@link net.benfro.scheduler.application.ScheduleCalculationService} nor
 * {@link net.benfro.scheduler.adapter.out.timefold.TimefoldSolveScheduleAdapter} needed to
 * change one line for this endpoint to exist.
 *
 * <p>Lives in {@code net.benfro.rest}, a sibling package to {@code net.benfro.presched}
 * rather than nested under {@code presched.adapter.in} - keeps the REST transport concern
 * external to the presched module's own package tree while still depending inward on its
 * application core, same as any other driving adapter would.
 *
 * <p>Reuses {@link ScheduleRequest}/{@link ScheduleResult} directly as the JSON wire types
 * rather than introducing dedicated REST DTOs - a deliberate simplification for this
 * early-stage service, since both were already shaped like a request/response payload (see
 * their javadoc). A stricter design would map to REST-only request/response records here
 * instead, keeping the application core's DTOs from doubling as wire formats.
 */
@Path("/schedules")
public class ScheduleResource {

    private final CalculateScheduleUseCase calculateSchedule;

    @Inject
    public ScheduleResource(CalculateScheduleUseCase calculateSchedule) {
        this.calculateSchedule = calculateSchedule;
    }

    /** Calculates and returns a full schedule for the given teachers/group/week - see {@link ScheduleRequest}. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ScheduleResult calculate(ScheduleRequest request) {
        return calculateSchedule.calculate(request);
    }
}
