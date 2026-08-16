package net.benfro.scheduler.application;

import java.time.LocalDate;
import java.util.List;

import net.benfro.scheduler.domain.CoverageRequirement;
import net.benfro.scheduler.domain.TeacherRoster;

/**
 * Output of {@link net.benfro.scheduler.application.port.in.CalculateScheduleUseCase}: the
 * solved {@link TeacherRoster} (score + every {@link net.benfro.scheduler.domain.TeacherSlot}),
 * alongside the {@code week} and {@code coverageRequirements} it was solved against, so a
 * driving adapter can render or verify the result without recomputing either.
 */
public record ScheduleResult(List<LocalDate> week, List<CoverageRequirement> coverageRequirements, TeacherRoster roster) {
}
