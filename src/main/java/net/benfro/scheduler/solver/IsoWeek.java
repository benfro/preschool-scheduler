package net.benfro.scheduler.solver;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

/** ISO week-based-year + week-of-year identity, used to group entities by calendar week regardless of year boundaries. */
public record IsoWeek(int year, int week) {

    public static IsoWeek of(LocalDate date) {
        return new IsoWeek(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
