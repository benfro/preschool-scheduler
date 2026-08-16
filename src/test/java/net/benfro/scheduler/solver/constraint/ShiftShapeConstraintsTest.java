package net.benfro.scheduler.solver.constraint;

import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.DATE;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.onDutySlots;

import java.time.LocalDate;
import java.util.ArrayList;
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

    // ************************************************************************
    // planningSessionTooLong
    // ************************************************************************

    @ParameterizedTest(name = "{0}-slot day, planning at {1} -> penalty {2}")
    @MethodSource("planningSessionOverflowScenarios")
    void planningSessionOverflow(int baseSlotCount, List<Integer> planningIndices, int expectedPenalty) {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, baseSlotCount, SlotActivity.BREAK);
        planningIndices.forEach(index -> slots.get(index).setActivity(SlotActivity.PLANNING_TIME));

        SingleConstraintAssertion assertion = constraintVerifier
                .verifyThat((provider, factory) -> ShiftShapeConstraints.planningSessionTooLong(factory))
                .given(slots.toArray());
        if (expectedPenalty == 0) {
            assertion.hasNoImpact();
        } else {
            assertion.penalizesBy(expectedPenalty);
        }
    }

    static Stream<Arguments> planningSessionOverflowScenarios() {
        return Stream.of(
                Arguments.of(6, List.of(), 0), // no planning time at all
                Arguments.of(6, List.of(2, 3), 0), // exactly the 2-slot max, no impact
                Arguments.of(6, List.of(1, 2, 3), 1), // 3-slot session, 1 over the max
                Arguments.of(10, List.of(0, 1, 2, 3), 2), // 4-slot session, 2 over the max
                Arguments.of(10, List.of(1, 2, 6, 7, 8), 1)); // one at-max session (no impact) plus one 3-slot session (1 over)
    }

    // ************************************************************************
    // tooManyPlanningSessionsPerDay
    // ************************************************************************

    @ParameterizedTest(name = "planning at {0} -> penalty {1}")
    @MethodSource("planningSessionCountScenarios")
    void planningSessionCount(List<Integer> planningIndices, int expectedPenalty) {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.BREAK);
        planningIndices.forEach(index -> slots.get(index).setActivity(SlotActivity.PLANNING_TIME));

        SingleConstraintAssertion assertion = constraintVerifier
                .verifyThat((provider, factory) -> ShiftShapeConstraints.tooManyPlanningSessionsPerDay(factory))
                .given(slots.toArray());
        if (expectedPenalty == 0) {
            assertion.hasNoImpact();
        } else {
            assertion.penalizesBy(expectedPenalty);
        }
    }

    static Stream<Arguments> planningSessionCountScenarios() {
        return Stream.of(
                Arguments.of(List.of(), 0), // no planning at all
                Arguments.of(List.of(2, 3), 0), // one contiguous session
                Arguments.of(List.of(2, 3, 7, 8), 1), // two separate sessions
                Arguments.of(List.of(1, 2, 5, 6, 8, 9), 2)); // three separate sessions
    }

    // ************************************************************************
    // planningSessionsNeedWorkdayGap
    // ************************************************************************

    /** Every slot of each given date, explicitly {@code OFF_DUTY} by default so every date shows up as one of the teacher's workdays. */
    private static List<TeacherSlot> weekOfDays(Teacher teacher, LocalDate... dates) {
        List<TeacherSlot> all = new ArrayList<>();
        for (LocalDate date : dates) {
            List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, date);
            daySlots.forEach(slot -> slot.setActivity(SlotActivity.OFF_DUTY));
            all.addAll(daySlots);
        }
        return all;
    }

    @Test
    void planningOnCalendarAdjacentWorkdaysIsPenalized() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = weekOfDays(teacher, DATE, DATE.plusDays(1), DATE.plusDays(2));
        slots.get(0).setActivity(SlotActivity.PLANNING_TIME); // Monday, slot 0
        slots.get(21).setActivity(SlotActivity.PLANNING_TIME); // Tuesday, slot 0 - no workday between them

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.planningSessionsNeedWorkdayGap(factory))
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void planningWithAWorkdayGapHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = weekOfDays(teacher, DATE, DATE.plusDays(1), DATE.plusDays(2));
        slots.get(0).setActivity(SlotActivity.PLANNING_TIME); // Monday
        slots.get(42).setActivity(SlotActivity.PLANNING_TIME); // Wednesday - Tuesday sits between them, unplanned

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.planningSessionsNeedWorkdayGap(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void threeConsecutivePlanningWorkdaysIsPenalizedTwice() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = weekOfDays(teacher, DATE, DATE.plusDays(1), DATE.plusDays(2));
        slots.get(0).setActivity(SlotActivity.PLANNING_TIME); // Monday
        slots.get(21).setActivity(SlotActivity.PLANNING_TIME); // Tuesday
        slots.get(42).setActivity(SlotActivity.PLANNING_TIME); // Wednesday - two adjacent pairs: Mon/Tue, Tue/Wed

        constraintVerifier.verifyThat((provider, factory) -> ShiftShapeConstraints.planningSessionsNeedWorkdayGap(factory))
                .given(slots.toArray())
                .penalizesBy(2);
    }
}
