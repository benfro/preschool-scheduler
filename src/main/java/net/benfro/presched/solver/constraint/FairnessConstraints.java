package net.benfro.presched.solver.constraint;

import static net.benfro.presched.solver.SchedulingConstants.MIDDAY;

import java.math.BigDecimal;
import java.math.RoundingMode;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.common.LoadBalance;

import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.TeacherSlot;
import net.benfro.presched.solver.SchedulingConstants;

/** Soft constraints keeping early/late teaching load evenly spread across teachers. */
public final class FairnessConstraints {

    private FairnessConstraints() {
    }

    public static Constraint[] define(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                balanceEarlySlotsAcrossTeachers(constraintFactory),
                balanceLateSlotsAcrossTeachers(constraintFactory),
        };
    }

    /** Prefer an even spread of before-{@link SchedulingConstants#MIDDAY} teaching slots across teachers. */
    static Constraint balanceEarlySlotsAcrossTeachers(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> teacherSlot.getActivity() instanceof SlotActivity.Teaching
                        && teacherSlot.getSlot().start().isBefore(MIDDAY))
                .groupBy(ConstraintCollectors.loadBalance(TeacherSlot::getTeacher))
                .penalize(HardSoftScore.ONE_SOFT, FairnessConstraints::unfairnessScore)
                .asConstraint("Balance early slots across teachers");
    }

    /** Prefer an even spread of {@link SchedulingConstants#MIDDAY}-or-later teaching slots across teachers. */
    static Constraint balanceLateSlotsAcrossTeachers(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TeacherSlot.class)
                .filter(teacherSlot -> teacherSlot.getActivity() instanceof SlotActivity.Teaching
                        && !teacherSlot.getSlot().start().isBefore(MIDDAY))
                .groupBy(ConstraintCollectors.loadBalance(TeacherSlot::getTeacher))
                .penalize(HardSoftScore.ONE_SOFT, FairnessConstraints::unfairnessScore)
                .asConstraint("Balance late slots across teachers");
    }

    /**
     * {@link HardSoftScore} is long-based, but {@link LoadBalance#unfairness()} is a
     * small fractional {@link BigDecimal} (well under 1.0 even for a badly skewed split)
     * — scale it up so imbalance still registers instead of truncating to zero. Kept
     * modest (200, not the 10,000 tried initially): scaling this too high made balance
     * dominate every other soft signal so completely that the solver would refuse moves
     * that reallocated break time into teaching time — even moves that couldn't possibly
     * unbalance early/late (both teachers gain identically) — because they weren't
     * literally the single best available move by this constraint's inflated arithmetic.
     * That starved {@code PreferenceConstraints.weeklyOnDutyBelowTarget} of the search
     * room it needed and capped every teacher's on-duty time well below their weekly
     * target.
     */
    private static final BigDecimal UNFAIRNESS_SCALE = BigDecimal.valueOf(200);

    private static long unfairnessScore(LoadBalance<?> loadBalance) {
        return loadBalance.unfairness().multiply(UNFAIRNESS_SCALE).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
