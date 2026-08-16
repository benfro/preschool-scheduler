package net.benfro.scheduler.solver.constraint;

import static net.benfro.scheduler.solver.SchedulingConstants.MINUTES_PER_HOUR;
import static net.benfro.scheduler.solver.SchedulingConstants.SLOT_MINUTES;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;

import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.solver.IsoWeek;
import net.benfro.scheduler.solver.SlotActivities;

/** Hard caps on a teacher's daily and weekly on-duty (teaching + planning) minutes. */
public final class WorkingHoursConstraints {

    private WorkingHoursConstraints() {
    }

    public static Constraint[] define(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                teacherDailyHoursExceeded(constraintFactory),
                teacherWeeklyHoursExceeded(constraintFactory),
        };
    }

    /** A teacher's on-duty (teaching + planning) time in one day must not exceed their daily cap. */
    static Constraint teacherDailyHoursExceeded(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> SlotActivities.countsTowardWorkingHours(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, TeacherSlot::date, ConstraintCollectors.count())
                .filter((teacher, date, slotCount) -> slotCount * SLOT_MINUTES > teacher.dailyHoursMax() * MINUTES_PER_HOUR)
                .penalize(HardSoftScore.ONE_HARD,
                        (teacher, date, slotCount) -> slotCount * SLOT_MINUTES - teacher.dailyHoursMax() * MINUTES_PER_HOUR)
                .asConstraint("Teacher daily hours exceeded");
    }

    /** A teacher's on-duty (teaching + planning) time in one ISO week must not exceed their weekly cap. */
    static Constraint teacherWeeklyHoursExceeded(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> SlotActivities.countsTowardWorkingHours(teacherSlot.getActivity()))
                .groupBy(TeacherSlot::getTeacher, teacherSlot -> IsoWeek.of(teacherSlot.date()), ConstraintCollectors.count())
                .filter((teacher, week, slotCount) -> slotCount * SLOT_MINUTES > teacher.hoursPerWeek() * MINUTES_PER_HOUR)
                .penalize(HardSoftScore.ONE_HARD,
                        (teacher, week, slotCount) -> slotCount * SLOT_MINUTES - teacher.hoursPerWeek() * MINUTES_PER_HOUR)
                .asConstraint("Teacher weekly hours exceeded");
    }
}
