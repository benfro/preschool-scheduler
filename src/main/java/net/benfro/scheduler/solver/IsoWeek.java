package net.benfro.scheduler.solver;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

/**
 * ISO week-based-year + week-of-year identity, used to group entities by calendar week
 * regardless of year boundaries. Named {@code weekBasedYear} (not plain {@code year}) since
 * it can disagree with {@link LocalDate#getYear()} for dates in the first/last days of
 * January/December that belong to a week-based year other than their calendar year.
 */
public record IsoWeek(int weekBasedYear, int weekOfYear) {

    public static IsoWeek of(LocalDate date) {
        return new IsoWeek(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
