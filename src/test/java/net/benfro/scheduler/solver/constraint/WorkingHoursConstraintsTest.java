package net.benfro.scheduler.solver.constraint;

import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.DATE;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.onDutySlots;
import static net.benfro.scheduler.solver.constraint.ConstraintTestFixtures.weekOfOnDutySlots;

import java.util.List;

import org.junit.jupiter.api.Test;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;

import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.Teacher;
import net.benfro.scheduler.domain.TeacherRoster;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.solver.TeacherScheduleConstraintProvider;

class WorkingHoursConstraintsTest {

    private final ConstraintVerifier<TeacherScheduleConstraintProvider, TeacherRoster> constraintVerifier =
            ConstraintVerifier.build(new TeacherScheduleConstraintProvider(), TeacherRoster.class, TeacherSlot.class);

    // ************************************************************************
    // teacherDailyHoursExceeded
    // ************************************************************************

    @Test
    void dailyHoursWithinCapHasNoImpact() {
        Teacher teacher = new Teacher("Alice", null); // dailyHoursMax = 8h = 16 half-hour slots
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 16, SlotActivity.PLANNING_TIME);

        constraintVerifier.verifyThat((provider, factory) -> WorkingHoursConstraints.teacherDailyHoursExceeded(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void dailyHoursBeyondCapIsPenalizedByExcessMinutes() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 17, SlotActivity.PLANNING_TIME); // 8.5h

        constraintVerifier.verifyThat((provider, factory) -> WorkingHoursConstraints.teacherDailyHoursExceeded(factory))
                .given(slots.toArray())
                .penalizesBy(30);
    }

    @Test
    void breakSlotsDoNotCountTowardDailyHours() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = onDutySlots(teacher, DATE, 16, SlotActivity.PLANNING_TIME);
        slots.addAll(onDutySlots(teacher, DATE, 4, SlotActivity.BREAK)); // extends the day, not the cap

        constraintVerifier.verifyThat((provider, factory) -> WorkingHoursConstraints.teacherDailyHoursExceeded(factory))
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

        constraintVerifier.verifyThat((provider, factory) -> WorkingHoursConstraints.teacherWeeklyHoursExceeded(factory))
                .given(slots.toArray())
                .hasNoImpact();
    }

    @Test
    void weeklyHoursBeyondCapIsPenalizedByExcessMinutes() {
        Teacher teacher = new Teacher("Alice", null);
        List<TeacherSlot> slots = weekOfOnDutySlots(teacher, 21, 21, 21, 18); // 81 slots = 40.5h

        constraintVerifier.verifyThat((provider, factory) -> WorkingHoursConstraints.teacherWeeklyHoursExceeded(factory))
                .given(slots.toArray())
                .penalizesBy(30);
    }
}
