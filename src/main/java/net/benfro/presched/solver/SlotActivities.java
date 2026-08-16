package net.benfro.presched.solver;

import net.benfro.presched.domain.Group;
import net.benfro.presched.domain.SlotActivity;
import net.benfro.presched.domain.TeacherSlot;

/**
 * Per-activity classification shared across the constraint groups in
 * {@link net.benfro.presched.solver.constraint} - pulled out so every group agrees on what
 * "on duty" and "counts toward the hour caps" mean, instead of each redefining it. Kept as
 * exhaustive switches over the sealed {@link SlotActivity} (plus an explicit {@code null}
 * guard) so a new variant is a compile error here, not a silent miscount.
 */
public final class SlotActivities {

    private SlotActivities() {
    }

    /** Minutes counted toward the daily/weekly hour caps — teaching + planning only; breaks are unpaid. */
    public static boolean countsTowardWorkingHours(SlotActivity activity) {
        if (activity == null) {
            return false;
        }
        return switch (activity) {
            case SlotActivity.Teaching _ -> true;
            case SlotActivity.PlanningTime _ -> true;
            case SlotActivity.Break _ -> false;
            case SlotActivity.OffDuty _ -> false;
        };
    }

    /** On duty covers teaching, break and planning time — everything except {@link SlotActivity#OFF_DUTY} (or {@code null}). */
    public static boolean isOnDuty(SlotActivity activity) {
        if (activity == null) {
            return false;
        }
        return switch (activity) {
            case SlotActivity.Teaching _ -> true;
            case SlotActivity.Break _ -> true;
            case SlotActivity.PlanningTime _ -> true;
            case SlotActivity.OffDuty _ -> false;
        };
    }

    /** The {@link Group} being taught in this slot, or {@code null} if it isn't a {@link SlotActivity.Teaching} slot. */
    public static Group teachingGroupOf(TeacherSlot teacherSlot) {
        return teacherSlot.getActivity() instanceof SlotActivity.Teaching teaching ? teaching.group() : null;
    }
}
