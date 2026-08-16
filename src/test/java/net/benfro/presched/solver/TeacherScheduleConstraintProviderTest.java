package net.benfro.presched.solver;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;

import net.benfro.presched.domain.CoverageRequirement;
import net.benfro.presched.domain.Group;
import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.Teacher;
import net.benfro.presched.domain.TeacherRoster;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.domain.TimeSlot;

class TeacherScheduleConstraintProviderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 17).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    private final ConstraintVerifier<TeacherScheduleConstraintProvider, TeacherRoster> constraintVerifier =
            ConstraintVerifier.build(new TeacherScheduleConstraintProvider(), TeacherRoster.class, TeacherSlot.class);

    // ************************************************************************
    // groupCoverageGap
    // ************************************************************************

    @Test
    void uncoveredRequirementIsPenalized() {
        Group group = new Group("Ducklings", List.of());
        CoverageRequirement requirement = new CoverageRequirement(group, morningSlot());

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::groupCoverageGap)
                .given(requirement)
                .penalizesBy(1);
    }

    @Test
    void requirementCoveredByATeachingSlotHasNoImpact() {
        Group group = new Group("Ducklings", List.of());
        TimeSlot slot = morningSlot();
        CoverageRequirement requirement = new CoverageRequirement(group, slot);
        Teacher teacher = new Teacher("Alice", null);
        TeacherSlot teacherSlot = new TeacherSlot("1", teacher, slot, new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::groupCoverageGap)
                .given(requirement, teacherSlot)
                .hasNoImpact();
    }

    @Test
    void requirementCoveredByABreakSlotIsStillPenalized() {
        Group group = new Group("Ducklings", List.of());
        TimeSlot slot = morningSlot();
        CoverageRequirement requirement = new CoverageRequirement(group, slot);
        Teacher teacher = new Teacher("Alice", null);
        TeacherSlot teacherSlot = new TeacherSlot("1", teacher, slot, SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::groupCoverageGap)
                .given(requirement, teacherSlot)
                .penalizesBy(1);
    }

    @Test
    void bothTeachersSharingAGroupOnBreakOrPlanningLeavesItUncovered() {
        // The scenario the user specifically called out: a group with two teachers is
        // still uncovered if *every* teacher attached to it is on break/planning at the
        // required moment - having "enough teachers" on paper doesn't help if none of
        // them are actually teaching right then.
        Group group = new Group("Ducklings", List.of());
        TimeSlot slot = morningSlot();
        CoverageRequirement requirement = new CoverageRequirement(group, slot);
        Teacher alice = new Teacher("Alice", group);
        Teacher bob = new Teacher("Bob", group);
        TeacherSlot aliceSlot = new TeacherSlot("a", alice, slot, SlotActivity.BREAK);
        TeacherSlot bobSlot = new TeacherSlot("b", bob, slot, SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::groupCoverageGap)
                .given(requirement, aliceSlot, bobSlot)
                .penalizesBy(1);
    }

    @Test
    void oneOfTwoTeachersSharingAGroupActuallyTeachingCoversIt() {
        Group group = new Group("Ducklings", List.of());
        TimeSlot slot = morningSlot();
        CoverageRequirement requirement = new CoverageRequirement(group, slot);
        Teacher alice = new Teacher("Alice", group);
        Teacher bob = new Teacher("Bob", group);
        TeacherSlot aliceSlot = new TeacherSlot("a", alice, slot, SlotActivity.BREAK);
        TeacherSlot bobSlot = new TeacherSlot("b", bob, slot, new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::groupCoverageGap)
                .given(requirement, aliceSlot, bobSlot)
                .hasNoImpact();
    }

    // ************************************************************************
    // teacherDailyHoursExceeded
    // ************************************************************************

    @Test
    void dailyHoursWithinCapHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null); // dailyHoursMax = 8h = 16 half-hour slots
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 16, SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::teacherDailyHoursExceeded)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void dailyHoursBeyondCapIsPenalizedByExcessMinutes() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 17, SlotActivity.PLANNING_TIME); // 8.5h

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::teacherDailyHoursExceeded)
                .given(slots.toArray())
                .penalizesBy(30);
    }

    @Test
    void breakSlotsDoNotCountTowardDailyHours() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 16, SlotActivity.PLANNING_TIME);
        slots.addAll(onDutySlots(teacher, DATE, 4, SlotActivity.BREAK)); // extends the day, not the cap

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::teacherDailyHoursExceeded)
                .given(slots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // teacherWeeklyHoursExceeded
    // ************************************************************************

    @Test
    void weeklyHoursWithinCapHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null); // hoursPerWeek = 40h = 80 half-hour slots
        List<TeacherSlot> slots = weekOfOnDutySlots(teacher, 21, 21, 21, 17); // 80 slots across 4 days

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::teacherWeeklyHoursExceeded)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void weeklyHoursBeyondCapIsPenalizedByExcessMinutes() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = weekOfOnDutySlots(teacher, 21, 21, 21, 18); // 81 slots = 40.5h

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::teacherWeeklyHoursExceeded)
                .given(slots.toArray())
                .penalizesBy(30);
    }

    // ************************************************************************
    // missingRequiredBreak
    // ************************************************************************

    @Test
    void sixHoursWithoutABreakIsPenalized() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 12, SlotActivity.PLANNING_TIME); // 6h, no break

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::missingRequiredBreak)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void sixHoursWithABreakHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 12, SlotActivity.PLANNING_TIME);
        slots.get(6).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::missingRequiredBreak)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void underSixHoursWithoutABreakHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 11, SlotActivity.PLANNING_TIME); // 5.5h

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::missingRequiredBreak)
                .given(slots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // tooManyBreakPeriods
    // ************************************************************************

    @Test
    void noBreaksHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::tooManyBreakPeriods)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void oneBreakPeriodHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(4).setActivity(SlotActivity.BREAK);
        slots.get(5).setActivity(SlotActivity.BREAK); // one contiguous 2-slot period

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::tooManyBreakPeriods)
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

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::tooManyBreakPeriods)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void twoSeparateBreakPeriodsHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 12, SlotActivity.PLANNING_TIME);
        slots.get(3).setActivity(SlotActivity.BREAK);
        slots.get(8).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::tooManyBreakPeriods)
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

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::tooManyBreakPeriods)
                .given(slots.toArray())
                .penalizesBy(1);
    }

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

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftMustBeContiguous)
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
        daySlots.get(0).setActivity(SlotActivity.PLANNING_TIME);
        daySlots.get(1).setActivity(SlotActivity.PLANNING_TIME);
        daySlots.get(2).setActivity(SlotActivity.OFF_DUTY);
        daySlots.get(3).setActivity(SlotActivity.OFF_DUTY);
        daySlots.get(4).setActivity(SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftMustBeContiguous)
                .given(daySlots.toArray())
                .penalizesBy(2);
    }

    @Test
    void embeddedOffDutyValueCountsAsAGapJustLikeUnassigned() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE);
        daySlots.get(0).setActivity(SlotActivity.PLANNING_TIME);
        daySlots.get(1).setActivity(SlotActivity.OFF_DUTY);
        daySlots.get(2).setActivity(SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftMustBeContiguous)
                .given(daySlots.toArray())
                .penalizesBy(1);
    }

    @Test
    void notWorkingThatDayAtAllHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftMustBeContiguous)
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    @Test
    void singleOnDutySlotHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE);
        daySlots.get(10).setActivity(SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftMustBeContiguous)
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

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftSpanExceedsCap)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void spanBeyondCapIsPenalizedByExcessMinutes() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 19, SlotActivity.PLANNING_TIME); // 570min, 30min over

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftSpanExceedsCap)
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

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftSpanExceedsCap)
                .given(slots.toArray())
                .penalizesBy(90); // 630min span - 540min cap
    }

    @Test
    void notWorkingThatDayHasNoSpanImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::shiftSpanExceedsCap)
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // planningSessionTooShort
    // ************************************************************************

    @Test
    void noPlanningTimeHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::planningSessionTooShort)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void twoAdjacentPlanningSlotsHaveNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.BREAK);
        slots.get(2).setActivity(SlotActivity.PLANNING_TIME);
        slots.get(3).setActivity(SlotActivity.PLANNING_TIME); // one contiguous 2-slot session

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::planningSessionTooShort)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void longerPlanningSessionHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::planningSessionTooShort)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void loneSinglePlanningSlotIsPenalizedByTheShortfall() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.BREAK);
        slots.get(3).setActivity(SlotActivity.PLANNING_TIME); // isolated single slot, min is 2

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::planningSessionTooShort)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void twoSeparateLonePlanningSlotsAreEachPenalized() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.BREAK);
        slots.get(2).setActivity(SlotActivity.PLANNING_TIME);
        slots.get(7).setActivity(SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::planningSessionTooShort)
                .given(slots.toArray())
                .penalizesBy(2);
    }

    // ************************************************************************
    // breakTooCloseToShiftEdge
    // ************************************************************************

    @Test
    void breakWellInsideTheShiftHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(4).setActivity(SlotActivity.BREAK); // 4 on-duty slots before it, 5 after

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::breakTooCloseToShiftEdge)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void breakAtTheVeryStartIsPenalized() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(0).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::breakTooCloseToShiftEdge)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void breakWithinTheBufferOfShiftStartIsPenalized() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(3).setActivity(SlotActivity.BREAK); // only 3 on-duty slots before it, buffer is 4

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::breakTooCloseToShiftEdge)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void breakWithinTheBufferOfShiftEndIsPenalized() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(6).setActivity(SlotActivity.BREAK); // only 3 on-duty slots after it (indices 7,8,9), buffer is 4

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::breakTooCloseToShiftEdge)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void breakExactlyAtTheBufferBoundaryHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME);
        slots.get(4).setActivity(SlotActivity.BREAK); // exactly 4 on-duty slots before it (indices 0-3)

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::breakTooCloseToShiftEdge)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void notWorkingThatDayHasNoBreakEdgeSpacingImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::breakTooCloseToShiftEdge)
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // weeklyPlanningTimeOffTarget
    // ************************************************************************

    @Test
    void noPlanningTimeAllWeekIsPenalizedByTheFullTarget() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 4, SlotActivity.BREAK); // on duty, but zero planning time

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyPlanningTimeOffTarget)
                .given(slots.toArray())
                .penalizesBy(120 * SchedulingConstants.PLANNING_TARGET_WEIGHT); // 2h target, 0 delivered
    }

    @Test
    void twoHoursOfPlanningTimeMeetsTheWeeklyTarget() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 4, SlotActivity.PLANNING_TIME); // 4 slots = 2h

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyPlanningTimeOffTarget)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void oneHourOfPlanningTimeIsPenalizedByTheShortfall() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 2, SlotActivity.PLANNING_TIME); // 2 slots = 1h

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyPlanningTimeOffTarget)
                .given(slots.toArray())
                .penalizesBy(60 * SchedulingConstants.PLANNING_TARGET_WEIGHT);
    }

    @Test
    void planningTimeAcrossTwoDaysOfTheSameWeekAccumulates() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = new java.util.ArrayList<>();
        slots.addAll(onDutySlots(teacher, DATE, 2, SlotActivity.PLANNING_TIME));
        slots.addAll(onDutySlots(teacher, DATE.plusDays(1), 2, SlotActivity.PLANNING_TIME));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyPlanningTimeOffTarget)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void moreThanTwoHoursOfPlanningTimeIsPenalizedByTheExcess() {
        // The bug this test guards against: a one-sided floor let planning time balloon
        // to hours beyond the 2h target with zero cost, since nothing else capped it.
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME); // 5h, 3h over target

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyPlanningTimeOffTarget)
                .given(slots.toArray())
                .penalizesBy(180 * SchedulingConstants.PLANNING_TARGET_WEIGHT);
    }

    // ************************************************************************
    // weeklyOnDutyBelowTarget
    // ************************************************************************

    @Test
    void wellBelowWeeklyTargetIsPenalizedByTheShortfall() {
        Teacher teacher = new Teacher("Alice", null); // hoursPerWeek = 40h
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME); // 5h

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyOnDutyBelowTarget)
                .given(slots.toArray())
                .penalizesBy(40 * 60 - 10 * SchedulingConstants.SLOT_MINUTES);
    }

    @Test
    void exactlyAtWeeklyTargetHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = weekOfOnDutySlots(teacher, 21, 21, 21, 17); // 80 slots = 40h, matches the hard cap exactly

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyOnDutyBelowTarget)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void breakTimeDoesNotCountTowardTheOnDutyTarget() {
        // Present all week (on duty in the loose sense) but every slot is an unpaid
        // break -> zero counted minutes, still the full shortfall.
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyOnDutyBelowTarget)
                .given(slots.toArray())
                .penalizesBy(40 * 60);
    }

    @Test
    void notWorkingThatWeekAtAllHasNoOnDutyTargetImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::weeklyOnDutyBelowTarget)
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // minimizeDistinctTeachersPerGroupPerDay
    // ************************************************************************

    @Test
    void twoTeachersForATinyLoadIsPenalizedAsAvoidableFragmentation() {
        // only 2 half-hour slots of teaching total -> one teacher could easily do both
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        TeacherSlot aliceSlot = new TeacherSlot("a", alice, morningSlot(), new SlotActivity.Teaching(group));
        TeacherSlot bobSlot = new TeacherSlot("b", bob, afternoonSlot(), new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::minimizeDistinctTeachersPerGroupPerDay)
                .given(aliceSlot, bobSlot)
                .penalizesBy(1);
    }

    @Test
    void oneTeacherAllDayHasNoImpact() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        TeacherSlot morning = new TeacherSlot("a", alice, morningSlot(), new SlotActivity.Teaching(group));
        TeacherSlot afternoon = new TeacherSlot("b", alice, afternoonSlot(), new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::minimizeDistinctTeachersPerGroupPerDay)
                .given(morning, afternoon)
                .hasNoImpact();
    }

    @Test
    void twoTeachersStructurallyRequiredByAFullDayOfPupilsHasNoImpact() {
        // 21 half-hour slots (the whole 07:00-17:30 day) split across exactly the 2
        // teachers that a single 8h/day teacher could never cover alone -> not fragmentation
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        List<TeacherSlot> slots = new java.util.ArrayList<>();
        slots.addAll(teachingSlots(group, alice, DATE, 0, 16));
        slots.addAll(teachingSlots(group, bob, DATE, 16, 21));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::minimizeDistinctTeachersPerGroupPerDay)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void threeTeachersForTheSameFullDayIsPenalizedForTheAvoidableExtraHandoff() {
        // same 21 slots as above, but split across 3 teachers instead of the 2 that suffice
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        Teacher cara = new Teacher("Cara", null);
        List<TeacherSlot> slots = new java.util.ArrayList<>();
        slots.addAll(teachingSlots(group, alice, DATE, 0, 10));
        slots.addAll(teachingSlots(group, bob, DATE, 10, 16));
        slots.addAll(teachingSlots(group, cara, DATE, 16, 21));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::minimizeDistinctTeachersPerGroupPerDay)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    // ************************************************************************
    // preferHomeGroupAssignment
    // ************************************************************************

    @Test
    void teachingHomeGroupIsRewarded() {
        Group home = new Group("Ducklings", List.of());
        Teacher teacher = new Teacher("Alice", home);
        TeacherSlot teacherSlot = new TeacherSlot("a", teacher, morningSlot(), new SlotActivity.Teaching(home));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::preferHomeGroupAssignment)
                .given(teacherSlot)
                .rewardsWith(1);
    }

    @Test
    void teachingAnotherGroupHasNoImpact() {
        Group home = new Group("Ducklings", List.of());
        Group other = new Group("Rabbits", List.of());
        Teacher teacher = new Teacher("Alice", home);
        TeacherSlot teacherSlot = new TeacherSlot("a", teacher, morningSlot(), new SlotActivity.Teaching(other));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::preferHomeGroupAssignment)
                .given(teacherSlot)
                .hasNoImpact();
    }

    // ************************************************************************
    // balanceEarlySlotsAcrossTeachers / balanceLateSlotsAcrossTeachers
    // ************************************************************************

    @Test
    void evenlySplitEarlySlotsHaveNoImpact() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        TeacherSlot aliceSlot = new TeacherSlot("a", alice, morningSlot(), new SlotActivity.Teaching(group));
        TeacherSlot bobSlot = new TeacherSlot("b", bob, morningSlot(), new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::balanceEarlySlotsAcrossTeachers)
                .given(aliceSlot, bobSlot)
                .hasNoImpact();
    }

    @Test
    void lopsidedEarlySlotsArePenalized() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        List<TeacherSlot> slots = new java.util.ArrayList<>();
        slots.addAll(teachingSlots(group, alice, DATE, 0, 4)); // 4 early slots
        slots.addAll(teachingSlots(group, bob, DATE, 4, 5)); // 1 early slot

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::balanceEarlySlotsAcrossTeachers)
                .given(slots.toArray())
                .penalizesByMoreThan(0);
    }

    @Test
    void evenlySplitLateSlotsHaveNoImpact() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        TeacherSlot aliceSlot = new TeacherSlot("a", alice, afternoonSlot(), new SlotActivity.Teaching(group));
        TeacherSlot bobSlot = new TeacherSlot("b", bob, afternoonSlot(), new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::balanceLateSlotsAcrossTeachers)
                .given(aliceSlot, bobSlot)
                .hasNoImpact();
    }

    @Test
    void lopsidedLateSlotsArePenalized() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        List<TeacherSlot> slots = new java.util.ArrayList<>();
        slots.addAll(teachingSlots(group, alice, DATE, 15, 20)); // 5 late slots
        slots.addAll(teachingSlots(group, bob, DATE, 20, 21)); // 1 late slot

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::balanceLateSlotsAcrossTeachers)
                .given(slots.toArray())
                .penalizesByMoreThan(0);
    }

    // ************************************************************************
    // avoidStartingOrEndingShiftWithBreak
    // ************************************************************************

    @Test
    void shiftStartingAndEndingWithNonBreakHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.PLANNING_TIME);
        slots.get(3).setActivity(SlotActivity.BREAK); // embedded break, not at an edge

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::avoidStartingOrEndingShiftWithBreak)
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void shiftStartingWithBreakIsPenalizedOnce() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.PLANNING_TIME);
        slots.get(0).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::avoidStartingOrEndingShiftWithBreak)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void shiftEndingWithBreakIsPenalizedOnce() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.PLANNING_TIME);
        slots.get(5).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::avoidStartingOrEndingShiftWithBreak)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void shiftStartingAndEndingWithBreakIsPenalizedTwice() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 6, SlotActivity.PLANNING_TIME);
        slots.get(0).setActivity(SlotActivity.BREAK);
        slots.get(5).setActivity(SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::avoidStartingOrEndingShiftWithBreak)
                .given(slots.toArray())
                .penalizesBy(2);
    }

    @Test
    void singleSlotBreakShiftIsPenalizedOnceNotTwice() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 1, SlotActivity.BREAK);

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::avoidStartingOrEndingShiftWithBreak)
                .given(slots.toArray())
                .penalizesBy(1);
    }

    @Test
    void notWorkingThatDayHasNoBreakEdgeImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat(TeacherScheduleConstraintProvider::avoidStartingOrEndingShiftWithBreak)
                .given(daySlots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // Helpers
    // ************************************************************************

    private static TimeSlot morningSlot() {
        return new TimeSlot(DATE, LocalTime.of(9, 0), LocalTime.of(9, 30));
    }

    private static TimeSlot afternoonSlot() {
        return new TimeSlot(DATE, LocalTime.of(14, 0), LocalTime.of(14, 30));
    }

    /** The first {@code count} grid slots of {@code date}, all set to {@code activity}. */
    private static List<TeacherSlot> onDutySlots(Teacher teacher, LocalDate date, int count, SlotActivity activity) {
        List<TeacherSlot> slots = ScheduleGenerator.teacherSlots(teacher, date).subList(0, count);
        slots.forEach(slot -> slot.setActivity(activity));
        return new java.util.ArrayList<>(slots);
    }

    /** {@code teacher} teaching {@code group} for grid slots [{@code fromInclusive}, {@code toExclusive}) of {@code date}. */
    private static List<TeacherSlot> teachingSlots(Group group, Teacher teacher, LocalDate date, int fromInclusive, int toExclusive) {
        List<TimeSlot> grid = ScheduleGenerator.dailySlots(date);
        List<TeacherSlot> slots = new java.util.ArrayList<>();
        for (int i = fromInclusive; i < toExclusive; i++) {
            slots.add(new TeacherSlot(teacher.name() + "-" + i, teacher, grid.get(i), new SlotActivity.Teaching(group)));
        }
        return slots;
    }

    /**
     * Planning-time slots for Monday through Thursday of the same ISO week (each count
     * capped at the 21 slots/day grid size), used to build up weekly totals beyond what
     * a single day's grid could hold.
     */
    private static List<TeacherSlot> weekOfOnDutySlots(Teacher teacher, int mondaySlots, int tuesdaySlots,
            int wednesdaySlots, int thursdaySlots) {
        List<TeacherSlot> slots = new java.util.ArrayList<>();
        slots.addAll(onDutySlots(teacher, DATE, mondaySlots, SlotActivity.PLANNING_TIME));
        slots.addAll(onDutySlots(teacher, DATE.plusDays(1), tuesdaySlots, SlotActivity.PLANNING_TIME));
        slots.addAll(onDutySlots(teacher, DATE.plusDays(2), wednesdaySlots, SlotActivity.PLANNING_TIME));
        slots.addAll(onDutySlots(teacher, DATE.plusDays(3), thursdaySlots, SlotActivity.PLANNING_TIME));
        return slots;
    }
}
