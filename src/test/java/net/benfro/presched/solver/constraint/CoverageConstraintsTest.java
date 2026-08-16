package net.benfro.presched.solver.constraint;

import static net.benfro.presched.solver.constraint.ConstraintTestFixtures.morningSlot;

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
import net.benfro.presched.solver.TeacherScheduleConstraintProvider;

class CoverageConstraintsTest {

    private final ConstraintVerifier<TeacherScheduleConstraintProvider, TeacherRoster> constraintVerifier =
            ConstraintVerifier.build(new TeacherScheduleConstraintProvider(), TeacherRoster.class, TeacherSlot.class);

    @Test
    void uncoveredRequirementIsPenalized() {
        Group group = new Group("Ducklings", List.of());
        CoverageRequirement requirement = new CoverageRequirement(group, morningSlot());

        constraintVerifier.verifyThat((provider, factory) -> CoverageConstraints.groupCoverageGap(factory))
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

        constraintVerifier.verifyThat((provider, factory) -> CoverageConstraints.groupCoverageGap(factory))
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

        constraintVerifier.verifyThat((provider, factory) -> CoverageConstraints.groupCoverageGap(factory))
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

        constraintVerifier.verifyThat((provider, factory) -> CoverageConstraints.groupCoverageGap(factory))
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

        constraintVerifier.verifyThat((provider, factory) -> CoverageConstraints.groupCoverageGap(factory))
                .given(requirement, aliceSlot, bobSlot)
                .hasNoImpact();
    }
}
