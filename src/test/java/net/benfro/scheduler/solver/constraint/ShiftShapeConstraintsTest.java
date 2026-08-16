package net.benfro.scheduler.solver.constraint;

import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.DATE;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.onDutySlots;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import ai.timefold.solver.core.api.score.stream.test.SingleConstraintAssertion;

import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TeacherRoster;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.solver.ScheduleGenerator;
import net.benfro.scheduler.solver.TeacherScheduleConstraintProvider;

class ShiftShapeConstraintsTest {

    private final ConstraintVerifier<TeacherScheduleConstraintProvider, TeacherRoster> constraintVerifier =
            ConstraintVerifier.build(new TeacherScheduleConstraintProvider(), TeacherRoster.class, TeacherSlot.class);

    // ************************************************************************
    // shiftMustBeContiguous
    // ************************************************************************

    @Test
    void contiguousBlockWithEmbeddedBreakHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE);
        for (int i = 0; i <= 4; i++) {
            daySlots.get(i).setActivity(SlotActivity.PLANNING_TIME);
        }
        daySlots.get(5).setActivity(SlotActivity.BREAK); // embedded break, shift still contiguous

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftMustBeContiguous(factory))
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    @Test
    void gapBetweenTwoOnDutyBlocksIsPenalizedByGapSlotCount() {
        // ConstraintVerifier's forEach() excludes entities with a literal null variable
        // (as does a real solve, mid-construction) - so the "gap" slots must carry the
        // explicit OFF_DUTY value here, exactly as they would in a fully solved roster,
        // not be left at the generator's null default.
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE);
        daySlots.getFirst().setActivity(SlotActivity.PLANNING_TIME);
        daySlots.get(1).setActivity(SlotActivity.PLANNING_TIME);
        daySlots.get(2).setActivity(SlotActivity.OFF_DUTY);
        daySlots.get(3).setActivity(SlotActivity.OFF_DUTY);
        daySlots.get(4).setActivity(SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftMustBeContiguous(factory))
                .given(daySlots.toArray())
                .penalizesBy(2);
    }

    @Test
    void embeddedOffDutyValueCountsAsAGapJustLikeUnassigned() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE);
        daySlots.getFirst().setActivity(SlotActivity.PLANNING_TIME);
        daySlots.get(1).setActivity(SlotActivity.OFF_DUTY);
        daySlots.get(2).setActivity(SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftMustBeContiguous(factory))
                .given(daySlots.toArray())
                .penalizesBy(1);
    }

    @Test
    void notWorkingThatDayAtAllHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftMustBeContiguous(factory))
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    @Test
    void singleOnDutySlotHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE);
        daySlots.get(10).setActivity(SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftMustBeContiguous(factory))
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // shiftSpanExceedsCap
    // ************************************************************************

    @Test
    void spanAtExactlyDailyCapPlusBreakAllowanceHasNoImpact() {
        // dailyHoursMax 8h (480min) + 60min break allowance = 540min = 18 slots
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 18, SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftSpanExceedsCap(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void spanBeyondCapIsPenalizedByExcessMinutes() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 19, SlotActivity.PLANNING_TIME); // 570min, 30min over

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftSpanExceedsCap(factory))
                .given(slots.toArray())
                .penalizesBy(30);
    }

    @Test
    void aFullDayPaddedEntirelyWithBreaksStillExceedsTheSpanCap() {
        // The exact "gap-free but padded with unlimited break" degenerate case this
        // constraint exists to forbid: 21 slots on duty (the whole opening window), only
        // 4 of them real teaching/planning, the rest break — still a 630min span.
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 21, SlotActivity.BREAK);
        for (int i = 0; i < 4; i++) {
            slots.get(i).setActivity(SlotActivity.PLANNING_TIME);
        }

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftSpanExceedsCap(factory))
                .given(slots.toArray())
                .penalizesBy(90); // 630min span - 540min cap
    }

    @Test
    void notWorkingThatDayHasNoSpanImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.shiftSpanExceedsCap(factory))
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // planningSessionTooShort
    // ************************************************************************

    /**
     * Every case starts from an all-{@code Break} day and overrides just the given
     * indices to {@code PlanningTime} - so "the whole day is one long session" is simply
     * every index in range, not a structurally different setup.
     */
    @ParameterizedTest(name = "{0}-slot day, planning at {1} -> penalty {2}")
    @MethodSource("planningSessionScenarios")
    void planningSessionShortfall(int baseSlotCount, List<Integer> planningIndices, int expectedPenalty) {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, baseSlotCount, SlotActivity.BREAK);
        planningIndices.forEach(index -> slots.get(index).setActivity(SlotActivity.PLANNING_TIME));

        SingleConstraintAssertion assertion = constraintVerifier
                .verifyThat((provider, factory) -> ShiftShapeConstraints.planningSessionTooShort(factory))
                .given(slots.toArray());
        if (expectedPenalty == 0) {
            assertion.hasNoImpact();
        } else {
            assertion.penalizesBy(expectedPenalty);
        }
    }

    static Stream<Arguments> planningSessionScenarios() {
        return Stream.of(
                Arguments.of(6, List.of(), 0), // no planning time at all
                Arguments.of(6, List.of(2, 3), 0), // one contiguous 2-slot session
                Arguments.of(6, List.of(0, 1, 2, 3, 4, 5), 0), // the whole day is one long session
                Arguments.of(6, List.of(3), 1), // isolated single slot, min is 2
                Arguments.of(10, List.of(2, 7), 2)); // two separate lone slots, each short by 1
    }
}
