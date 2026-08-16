package net.benfro.scheduler.application;

import java.time.LocalDate;
import java.util.List;

import net.benfro.scheduler.domain.Group;
import net.benfro.scheduler.domain.Teacher;

/**
 * Input to {@link net.benfro.scheduler.application.port.in.CalculateScheduleUseCase}: who
 * needs scheduling ({@code teachers}), for which group ({@code group}, pupils and their
 * staying times already populated), over which days ({@code week}). Framework-agnostic -
 * this is exactly what a future REST driving adapter would assemble from a request body,
 * not just what the CLI demo happens to generate.
 */
public record ScheduleRequest(List<Teacher> teachers, Group group, List<LocalDate> week) {
}
