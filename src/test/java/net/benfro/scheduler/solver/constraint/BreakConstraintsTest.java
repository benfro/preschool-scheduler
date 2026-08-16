package net.benfro.scheduler.solver.constraint;

import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.DATE;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.onDutySlots;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import ai.timefold.solver.core.api.score.stream.test.SingleConstraintAssertion;

import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TeacherRoster;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.solver.ScheduleGenerator;
import net.benfro.scheduler.solver.TeacherScheduleConstraintProvider;

class BreakConstraintsTest {

    private final ConstraintVerifier<TeacherScheduleConstraintProvider, TeacherRoster> constraintVerifier =
            ConstraintVerifier.build(new TeacherScheduleConstraintProvider(), TeacherRoster.class, TeacherSlot.class);

    // ************************************************************************
    // missingRequiredBreak
    // ************************************************************************

    @Test
    void sixHoursWithoutABreakIsPenalized() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 12, SlotActivity.PLANNING_TIME); // 6h, no break

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.missingRequiredBreak(factory))
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void sixHoursWithABreakHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 12, SlotActivity.PLANNING_TIME);
        slots.get(6).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.missingRequiredBreak(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void underSixHoursWithoutABreakHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 11, SlotActivity.PLANNING_TIME); // 5.5h

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.missingRequiredBreak(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // breakSlotsMustNotBeAdjacent
    // ************************************************************************

    @Test
    void singleSlotBreakHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(4).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.breakSlotsMustNotBeAdjacent(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void twoSeparateSingleSlotBreaksHaveNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(3).setActivity(SlotActivity.BREAK);
        slots.get(8).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.breakSlotsMustNotBeAdjacent(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void twoAdjacentBreakSlotsIsPenalizedOnce() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(4).setActivity(SlotActivity.BREAK);
        slots.get(5).setActivity(SlotActivity.BREAK); // one 2-slot period - 1 adjacent pair

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.breakSlotsMustNotBeAdjacent(factory))
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void fiveConsecutiveBreakSlotsIsPenalizedByFour() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        for (int i = 2; i <= 6; i++) {
            slots.get(i).setActivity(SlotActivity.BREAK); // 5 consecutive slots - 4 adjacent pairs
        }

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.breakSlotsMustNotBeAdjacent(factory))
                .given(slots.toArray())
                .penalizesBy(4);
    }

    // ************************************************************************
    // tooManyBreakPeriods
    // ************************************************************************

    @Test
    void noBreaksHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.tooManyBreakPeriods(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void oneBreakPeriodHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(4).setActivity(SlotActivity.BREAK);
        slots.get(5).setActivity(SlotActivity.BREAK); // one contiguous 2-slot period

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.tooManyBreakPeriods(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void oneLongBreakPeriodStillCountsAsOnePeriod() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        for (int i = 2; i <= 6; i++) {
            slots.get(i).setActivity(SlotActivity.BREAK); // 5 consecutive slots, one long period
        }

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.tooManyBreakPeriods(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void twoSeparateBreakPeriodsHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 12, SlotActivity.PLANNING_TIME);
        slots.get(3).setActivity(SlotActivity.BREAK);
        slots.get(8).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.tooManyBreakPeriods(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void threeSeparateBreakPeriodsIsPenalizedByTheExcess() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 15, SlotActivity.PLANNING_TIME);
        slots.get(2).setActivity(SlotActivity.BREAK);
        slots.get(7).setActivity(SlotActivity.BREAK);
        slots.get(12).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.tooManyBreakPeriods(factory))
                .given(slots.toArray())
                .penalizesBy(1);
    }

    // ************************************************************************
    // breakPeriodsTooCloseTogether
    // ************************************************************************

    @Test
    void singleBreakPeriodHasNoSpacingImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(4).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.breakPeriodsTooCloseTogether(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void twoBreakPeriodsExactlyThreeWorkHoursApartHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 20, SlotActivity.PLANNING_TIME);
        slots.get(2).setActivity(SlotActivity.BREAK); // period ends at index 2
        slots.get(9).setActivity(SlotActivity.BREAK); // 6 on-duty slots (3h) between indices 3-8, then break at 9

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.breakPeriodsTooCloseTogether(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void twoBreakPeriodsTooCloseTogetherIsPenalizedByShortfallMinutes() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 20, SlotActivity.PLANNING_TIME);
        slots.get(2).setActivity(SlotActivity.BREAK); // period ends at index 2
        slots.get(7).setActivity(SlotActivity.BREAK); // only 4 on-duty slots (2h) between them - 1h short

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.breakPeriodsTooCloseTogether(factory))
                .given(slots.toArray())
                .penalizesBy(60);
    }

    // ************************************************************************
    // breakTooCloseToShiftEdge
    // ************************************************************************

    /**
     * Same 10-slot on-duty shift each time, only the break's position varies - a textbook
     * {@code @ParameterizedTest} case. {@code breakIndex 4} covers what used to be two
     * separate (and identical) tests: "well inside the shift" and "exactly at the buffer
     * boundary" are the same slot.
     */
    @ParameterizedTest(name = "break at slot {0} -> penalty {1}")
    @CsvSource({
            "0, 1", // at the very start
            "3, 1", // within the start buffer (only 3 on-duty slots before it, buffer is 4)
            "4, 0", // exactly at the buffer boundary (4 on-duty slots before it) - no impact
            "6, 1", // within the end buffer (only 3 on-duty slots after it, buffer is 4)
    })
    void breakEdgeProximity(int breakIndex, int expectedPenalty) {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(breakIndex).setActivity(SlotActivity.BREAK);

        SingleConstraintAssertion assertion = constraintVerifier
                .verifyThat((provider, factory) -> BreakConstraints.breakTooCloseToShiftEdge(factory))
                .given(slots.toArray());
        if (expectedPenalty == 0) {
            assertion.hasNoImpact();
        } else {
            assertion.penalizesBy(expectedPenalty);
        }
    }

    @Test
    void notWorkingThatDayHasNoBreakEdgeSpacingImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.breakTooCloseToShiftEdge(factory))
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // avoidStartingOrEndingShiftWithBreak
    // ************************************************************************

    /** Same 6-slot on-duty shift each time, only which slots are a {@code Break} varies. */
    @ParameterizedTest(name = "breaks at {0} -> penalty {1}")
    @MethodSource("shiftEdgeBreakScenarios")
    void shiftEdgeBreakPlacement(List<Integer> breakIndices, int expectedPenalty) {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.PLANNING_TIME);
        breakIndices.forEach(index -> slots.get(index).setActivity(SlotActivity.BREAK));

        SingleConstraintAssertion assertion = constraintVerifier
                .verifyThat((provider, factory) -> BreakConstraints.avoidStartingOrEndingShiftWithBreak(factory))
                .given(slots.toArray());
        if (expectedPenalty == 0) {
            assertion.hasNoImpact();
        } else {
            assertion.penalizesBy(expectedPenalty);
        }
    }

    static Stream<Arguments> shiftEdgeBreakScenarios() {
        return Stream.of(
                Arguments.of(List.of(3), 0), // embedded break, not at an edge
                Arguments.of(List.of(0), 1), // starts with a break
                Arguments.of(List.of(5), 1), // ends with a break
                Arguments.of(List.of(0, 5), 2)); // starts and ends with a break
    }

    @Test
    void singleSlotBreakShiftIsPenalizedOnceNotTwice() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 1, SlotActivity.BREAK);

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.avoidStartingOrEndingShiftWithBreak(factory))
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void notWorkingThatDayHasNoBreakEdgeImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat((provider, factory) -> BreakConstraints.avoidStartingOrEndingShiftWithBreak(factory))
                .given(daySlots.toArray())
                .hasNoImpact();
    }
}
