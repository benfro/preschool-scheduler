package net.benfro.scheduler.solver.constraint;

import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.DATE;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.afternoonSlot;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.morningSlot;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.onDutySlots;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.teachingSlots;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.weekOfOnDutySlots;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import ai.timefold.solver.core.api.score.stream.test.SingleConstraintAssertion;

import net.benfro.scheduler.domain.Group;
import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TeacherRoster;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.solver.ScheduleGenerator;
import net.benfro.scheduler.solver.SchedulingConstants;
import net.benfro.scheduler.solver.TeacherScheduleConstraintProvider;

class PreferenceConstraintsTest {

    private final ConstraintVerifier<TeacherScheduleConstraintProvider, TeacherRoster> constraintVerifier =
            ConstraintVerifier.build(new TeacherScheduleConstraintProvider(), TeacherRoster.class, TeacherSlot.class);

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

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.minimizeDistinctTeachersPerGroupPerDay(factory))
                .given(aliceSlot, bobSlot)
                .penalizesBy(1);
    }

    @Test
    void oneTeacherAllDayHasNoImpact() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        TeacherSlot morning = new TeacherSlot("a", alice, morningSlot(), new SlotActivity.Teaching(group));
        TeacherSlot afternoon = new TeacherSlot("b", alice, afternoonSlot(), new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.minimizeDistinctTeachersPerGroupPerDay(factory))
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
        List<TeacherSlot> slots = new ArrayList<>();
        slots.addAll(teachingSlots(group, alice, DATE, 0, 16));
        slots.addAll(teachingSlots(group, bob, DATE, 16, 21));

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.minimizeDistinctTeachersPerGroupPerDay(factory))
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
        List<TeacherSlot> slots = new ArrayList<>();
        slots.addAll(teachingSlots(group, alice, DATE, 0, 10));
        slots.addAll(teachingSlots(group, bob, DATE, 10, 16));
        slots.addAll(teachingSlots(group, cara, DATE, 16, 21));

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.minimizeDistinctTeachersPerGroupPerDay(factory))
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

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.preferHomeGroupAssignment(factory))
                .given(teacherSlot)
                .rewardsWith(1);
    }

    @Test
    void teachingAnotherGroupHasNoImpact() {
        Group home = new Group("Ducklings", List.of());
        Group other = new Group("Rabbits", List.of());
        Teacher teacher = new Teacher("Alice", home);
        TeacherSlot teacherSlot = new TeacherSlot("a", teacher, morningSlot(), new SlotActivity.Teaching(other));

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.preferHomeGroupAssignment(factory))
                .given(teacherSlot)
                .hasNoImpact();
    }

    // ************************************************************************
    // weeklyPlanningTimeOffTarget
    // ************************************************************************

    /** Same single-day scenario shape - only the on-duty activity/slot count (hence delivered planning minutes) varies. */
    @ParameterizedTest(name = "{0} slot(s) of {1} -> penalty {2}")
    @MethodSource("weeklyPlanningTimeScenarios")
    void weeklyPlanningTimeDeviation(int slotCount, SlotActivity activity, long expectedPenalty) {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, slotCount, activity);

        SingleConstraintAssertion assertion = constraintVerifier
                .verifyThat((provider, factory) -> PreferenceConstraints.weeklyPlanningTimeOffTarget(factory))
                .given(slots.toArray());
        if (expectedPenalty == 0) {
            assertion.hasNoImpact();
        } else {
            assertion.penalizesBy(expectedPenalty);
        }
    }

    static Stream<Arguments> weeklyPlanningTimeScenarios() {
        return Stream.of(
                // on duty (via breaks), but zero planning delivered -> full 2h target as shortfall
                Arguments.of(4, SlotActivity.BREAK, 120L * SchedulingConstants.PLANNING_TARGET_WEIGHT),
                Arguments.of(4, SlotActivity.PLANNING_TIME, 0L), // 4 slots = 2h = exactly the target
                Arguments.of(2, SlotActivity.PLANNING_TIME, 60L * SchedulingConstants.PLANNING_TARGET_WEIGHT), // 1h, 1h short
                // The bug this case guards against: a one-sided floor let planning time
                // balloon to hours beyond the 2h target with zero cost.
                Arguments.of(10, SlotActivity.PLANNING_TIME, 180L * SchedulingConstants.PLANNING_TARGET_WEIGHT)); // 5h, 3h over
    }

    @Test
    void planningTimeAcrossTwoDaysOfTheSameWeekAccumulates() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = new ArrayList<>();
        slots.addAll(onDutySlots(teacher, DATE, 2, SlotActivity.PLANNING_TIME));
        slots.addAll(onDutySlots(teacher, DATE.plusDays(1), 2, SlotActivity.PLANNING_TIME));

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.weeklyPlanningTimeOffTarget(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    // ************************************************************************
    // weeklyOnDutyBelowTarget
    // ************************************************************************

    @Test
    void wellBelowWeeklyTargetIsPenalizedByTheShortfall() {
        Teacher teacher = new Teacher("Alice", null); // hoursPerWeek = 40h
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.PLANNING_TIME); // 5h

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.weeklyOnDutyBelowTarget(factory))
                .given(slots.toArray())
                .penalizesBy(40 * 60 - 10 * SchedulingConstants.SLOT_MINUTES);
    }

    @Test
    void exactlyAtWeeklyTargetHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = weekOfOnDutySlots(teacher, 21, 21, 21, 17); // 80 slots = 40h, matches the hard cap exactly

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.weeklyOnDutyBelowTarget(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void breakTimeDoesNotCountTowardTheOnDutyTarget() {
        // Present all week (on duty in the loose sense) but every slot is an unpaid
        // break -> zero counted minutes, still the full shortfall.
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 10, SlotActivity.BREAK);

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.weeklyOnDutyBelowTarget(factory))
                .given(slots.toArray())
                .penalizesBy(40 * 60);
    }

    @Test
    void notWorkingThatWeekAtAllHasNoOnDutyTargetImpact() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> daySlots = ScheduleGenerator.teacherSlots(teacher, DATE); // every slot left off duty

        constraintVerifier.verifyThat((provider, factory) -> PreferenceConstraints.weeklyOnDutyBelowTarget(factory))
                .given(daySlots.toArray())
                .hasNoImpact();
    }
}
