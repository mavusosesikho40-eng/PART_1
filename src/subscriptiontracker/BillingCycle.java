package subscriptiontracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * How often a subscription is charged.
 */
public enum BillingCycle {
    WEEKLY("Weekly", 52),
    MONTHLY("Monthly", 12),
    QUARTERLY("Quarterly", 4),
    YEARLY("Yearly", 1);

    private final String label;
    private final int paymentsPerYear;

    BillingCycle(String label, int paymentsPerYear) {
        this.label = label;
        this.paymentsPerYear = paymentsPerYear;
    }

    public String getLabel() {
        return label;
    }

    /** How often, as used after an amount: "a week", "a month", "a quarter" or "a year". */
    public String per() {
        return switch (this) {
            case WEEKLY -> "a week";
            case MONTHLY -> "a month";
            case QUARTERLY -> "a quarter";
            case YEARLY -> "a year";
        };
    }

    /** Converts a cost per cycle into the equivalent yearly cost. */
    public BigDecimal toYearly(BigDecimal cost) {
        return cost.multiply(BigDecimal.valueOf(paymentsPerYear));
    }

    /** Converts a cost per cycle into the equivalent monthly cost. */
    public BigDecimal toMonthly(BigDecimal cost) {
        return toYearly(cost).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    /** Returns the payment date that follows the given one. */
    public LocalDate next(LocalDate date) {
        return next(date, date.getDayOfMonth());
    }

    /**
     * Returns the payment date that follows the given one, for a subscription
     * billed on the given day of the month. In a month too short for that
     * day the last day of the month is used, and the next month goes back
     * to the billing day (31 Jan, 28 Feb, 31 Mar...). Weekly payments ignore it.
     */
    public LocalDate next(LocalDate date, int billingDay) {
        LocalDate next = switch (this) {
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case YEARLY -> date.plusYears(1);
        };
        if (this == WEEKLY) {
            return next;
        }
        return next.withDayOfMonth(Math.min(billingDay, next.lengthOfMonth()));
    }
}
