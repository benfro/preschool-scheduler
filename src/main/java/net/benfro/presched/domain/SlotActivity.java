package net.benfro.presched.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What a {@link Teacher} is doing during one {@link TeacherSlot}: teaching a specific
 * {@link Group}, taking a break, doing (non-child-facing) planning time, or off duty.
 * This is the planning variable's value type — its options are supplied by
 * {@code TeacherRoster#activityOptions()}.
 *
 * <p>Off duty is modeled as an explicit value ({@link #OFF_DUTY}) rather than leaving
 * the variable unassigned/null: this Timefold version's default Construction Heuristic
 * never explores real values for a variable configured with
 * {@code allowsUnassigned = true} — every entity stays unassigned indefinitely. Making
 * "off duty" a first-class value sidesteps that entirely.
 *
 * <p>Carries {@code @JsonTypeInfo}/{@code @JsonSubTypes} so the REST driving adapter's
 * JSON response includes a {@code "type"} discriminator per slot - without it, every
 * subtype but {@code Teaching} serializes to an indistinguishable {@code {}} (a plain
 * Jackson bean write doesn't know which sealed variant it's looking at). A stricter
 * hexagonal design would keep Jackson annotations out of the domain entirely and map to a
 * REST-only response type instead; this is a deliberate, small pragmatic exception for an
 * early-stage service, not a precedent to repeat casually elsewhere.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SlotActivity.Teaching.class, name = "TEACHING"),
        @JsonSubTypes.Type(value = SlotActivity.Break.class, name = "BREAK"),
        @JsonSubTypes.Type(value = SlotActivity.PlanningTime.class, name = "PLANNING_TIME"),
        @JsonSubTypes.Type(value = SlotActivity.OffDuty.class, name = "OFF_DUTY"),
})
public sealed interface SlotActivity {

    Break BREAK = new Break();
    PlanningTime PLANNING_TIME = new PlanningTime();
    OffDuty OFF_DUTY = new OffDuty();

    record Teaching(Group group) implements SlotActivity {
    }

    record Break() implements SlotActivity {
    }

    record PlanningTime() implements SlotActivity {
    }

    record OffDuty() implements SlotActivity {
    }
}
