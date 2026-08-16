package net.benfro.presched.domain;

/**
 * Problem fact: {@code group} needs at least one teacher actively teaching it during
 * {@code slot}. Generated only for slots where the group actually has a pupil present
 * (see {@code ScheduleGenerator#coverageRequirements}), not blanket opening hours.
 */
public record CoverageRequirement(Group group, TimeSlot slot) {
}
