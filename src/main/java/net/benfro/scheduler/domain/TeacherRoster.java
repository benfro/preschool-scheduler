package net.benfro.scheduler.domain;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardSoftScore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The planning solution: a set of {@link TeacherSlot}s (what each teacher does, per
 * 30-minute block) to fill in so that every {@link CoverageRequirement} is met, subject
 * to each {@link Teacher}'s daily/weekly hour caps.
 */
@PlanningSolution
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeacherRoster {

    @ProblemFactCollectionProperty
    private List<Teacher> teachers;

    @ProblemFactCollectionProperty
    private List<Group> groups;

    @ProblemFactCollectionProperty
    private List<CoverageRequirement> coverageRequirements;

    @PlanningEntityCollectionProperty
    private List<TeacherSlot> teacherSlots;

    @PlanningScore
    private HardSoftScore score;

    /** Every slot can either teach one of the known groups, be a break, planning time, or off duty. */
    @ValueRangeProvider(id = "activityRange")
    public List<SlotActivity> activityOptions() {
        List<SlotActivity> options = new ArrayList<>(groups.size() + 3);
        for (Group group : groups) {
            options.add(new SlotActivity.Teaching(group));
        }
        options.add(SlotActivity.BREAK);
        options.add(SlotActivity.PLANNING_TIME);
        options.add(SlotActivity.OFF_DUTY);
        return options;
    }
}
