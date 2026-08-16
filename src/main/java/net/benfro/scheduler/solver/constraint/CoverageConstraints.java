package net.benfro.scheduler.solver.constraint;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;

import net.benfro.scheduler.domain.CoverageRequirement;
import net.benfro.scheduler.domain.SlotActivity;
import net.benfro.scheduler.domain.TeacherSlot;
import net.benfro.scheduler.solver.SlotActivities;

/** Hard constraints guaranteeing every {@link CoverageRequirement} is actually met by a teacher teaching. */
public final class CoverageConstraints {

    private CoverageConstraints() {
    }

    public static Constraint[] define(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                groupCoverageGap(constraintFactory),
        };
    }

    /**
     * Every {@link CoverageRequirement} must be met by a teacher actually teaching that
     * group then — {@link SlotActivity.Teaching}, specifically. This is what guarantees a
     * group is never left without care: it doesn't matter how many teachers have that
     * group as their home group or are otherwise attached to it — if every one of them is
     * on a {@link SlotActivity.Break} or {@link SlotActivity.PlanningTime} at the same
     * moment a pupil is present, that moment still has zero matching {@link TeacherSlot}
     * and is penalized exactly like having no teacher assigned at all.
     */
    static Constraint groupCoverageGap(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(CoverageRequirement.class)
                .ifNotExists(TeacherSlot.class,
                        Joiners.equal(CoverageRequirement::slot, TeacherSlot::getSlot),
                        Joiners.equal(CoverageRequirement::group, SlotActivities::teachingGroupOf))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group coverage gap");
    }
}
