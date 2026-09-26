package subscriptiontracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * A change to what a subscription costs, e.g. Netflix going from 169.00 to
 * 199.00 a month. The billing cycle is kept too, because a plan can change
 * from monthly to yearly at the same time (199.00 a month to 2,000.00 a year).
 */
public record PriceChange(LocalDate date, BigDecimal oldCost, BigDecimal newCost,
        BillingCycle oldCycle, BillingCycle newCycle) {

    /** A price change that kept the same billing cycle. */
    public PriceChange(LocalDate date, BigDecimal oldCost, BigDecimal newCost, BillingCycle cycle) {
        this(date, oldCost, newCost, cycle, cycle);
    }

    public boolean cycleChanged() {
        return oldCycle != newCycle;
    }

    /** How much more (or, if negative, less) each payment costs now. */
    public BigDecimal difference() {
        return newCost.subtract(oldCost);
    }

    /** How much more (or, if negative, less) it costs per month now. */
    public BigDecimal monthlyDifference() {
        return newCycle.toMonthly(newCost).subtract(oldCycle.toMonthly(oldCost));
    }

    /**
     * The change as a whole-number percentage: of the old price per payment,
     * or of the old monthly cost when the billing cycle changed too. Null if
     * the old price was zero.
     */
    public BigDecimal percent() {
        BigDecimal before = cycleChanged() ? oldCycle.toMonthly(oldCost) : oldCost;
        BigDecimal change = cycleChanged() ? monthlyDifference() : difference();
        if (before.signum() == 0) {
            return null;
        }
        return change.multiply(BigDecimal.valueOf(100)).divide(before, 0, RoundingMode.HALF_UP);
    }
}
