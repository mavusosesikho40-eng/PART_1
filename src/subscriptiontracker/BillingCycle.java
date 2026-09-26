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
        return switch (this) {
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case YEARLY -> date.plusYears(1);
        };
    }
}
