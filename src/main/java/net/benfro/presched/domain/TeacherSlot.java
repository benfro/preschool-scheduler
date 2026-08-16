package net.benfro.presched.domain;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Planning entity: what one {@link Teacher} is doing during one fixed 30-minute
 * {@link TimeSlot} of one day. {@code teacher} and {@code slot} are fixed (problem facts);
 * {@code activity} is what the solver decides, one of the {@link SlotActivity} options
 * including {@link SlotActivity#OFF_DUTY}. {@code activity} is {@code null} only before
 * solving assigns it a real value.
 *
 * <p>Because {@code activity} is a single planning variable per (teacher, slot) pair, a
 * teacher can never be double-booked — it's structurally impossible, not something a
 * constraint has to forbid.
 */
@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TeacherSlot {

    @PlanningId
    private String id;

    private Teacher teacher;
    private TimeSlot slot;

    @PlanningVariable(valueRangeProviderRefs = "activityRange")
    private SlotActivity activity;
}
