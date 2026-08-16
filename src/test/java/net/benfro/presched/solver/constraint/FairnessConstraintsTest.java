package net.benfro.presched.solver.constraint;

import static net.benfro.presched.solver.constraint.ConstraintTestFixtures.DATE;
import static net.benfro.presched.solver.constraint.ConstraintTestFixtures.afternoonSlot;
import static net.benfro.presched.solver.constraint.ConstraintTestFixtures.morningSlot;
import static net.benfro.presched.solver.constraint.ConstraintTestFixtures.teachingSlots;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;

import net.benfro.presched.domain.Group;
import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.Teacher;
import net.benfro.presched.domain.TeacherRoster;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.solver.TeacherScheduleConstraintProvider;

class FairnessConstraintsTest {

    private final ConstraintVerifier<TeacherScheduleConstraintProvider, TeacherRoster> constraintVerifier =
            ConstraintVerifier.build(new TeacherScheduleConstraintProvider(), TeacherRoster.class, TeacherSlot.class);

    // ************************************************************************
    // balanceEarlySlotsAcrossTeachers
    // ************************************************************************

    @Test
    void evenlySplitEarlySlotsHaveNoImpact() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        TeacherSlot aliceSlot = new TeacherSlot("a", alice, morningSlot(), new SlotActivity.Teaching(group));
        TeacherSlot bobSlot = new TeacherSlot("b", bob, morningSlot(), new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat((provider, factory) -> FairnessConstraints.balanceEarlySlotsAcrossTeachers(factory))
                .given(aliceSlot, bobSlot)
                .hasNoImpact();
    }

    @Test
    void lopsidedEarlySlotsArePenalized() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        List<TeacherSlot> slots = new ArrayList<>();
        slots.addAll(teachingSlots(group, alice, DATE, 0, 4)); // 4 early slots
        slots.addAll(teachingSlots(group, bob, DATE, 4, 5)); // 1 early slot

        constraintVerifier.verifyThat((provider, factory) -> FairnessConstraints.balanceEarlySlotsAcrossTeachers(factory))
                .given(slots.toArray())
                .penalizesByMoreThan(0);
    }

    // ************************************************************************
    // balanceLateSlotsAcrossTeachers
    // ************************************************************************

    @Test
    void evenlySplitLateSlotsHaveNoImpact() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        TeacherSlot aliceSlot = new TeacherSlot("a", alice, afternoonSlot(), new SlotActivity.Teaching(group));
        TeacherSlot bobSlot = new TeacherSlot("b", bob, afternoonSlot(), new SlotActivity.Teaching(group));

        constraintVerifier.verifyThat((provider, factory) -> FairnessConstraints.balanceLateSlotsAcrossTeachers(factory))
                .given(aliceSlot, bobSlot)
                .hasNoImpact();
    }

    @Test
    void lopsidedLateSlotsArePenalized() {
        Group group = new Group("Ducklings", List.of());
        Teacher alice = new Teacher("Alice", null);
        Teacher bob = new Teacher("Bob", null);
        List<TeacherSlot> slots = new ArrayList<>();
        slots.addAll(teachingSlots(group, alice, DATE, 15, 20)); // 5 late slots
        slots.addAll(teachingSlots(group, bob, DATE, 20, 21)); // 1 late slot

        constraintVerifier.verifyThat((provider, factory) -> FairnessConstraints.balanceLateSlotsAcrossTeachers(factory))
                .given(slots.toArray())
                .penalizesByMoreThan(0);
    }
}
