package subscriptiontracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * A change to what a subscription costs per payment, e.g. Netflix going
 * from 169.00 to 199.00 on a given day.
 */
public record PriceChange(LocalDate date, BigDecimal oldCost, BigDecimal newCost) {

    /** How much more (or, if negative, less) each payment costs now. */
    public BigDecimal difference() {
        return newCost.subtract(oldCost);
    }

    /** The change as a whole-number percentage of the old price, or null if the old price was zero. */
    public BigDecimal percent() {
        if (oldCost.signum() == 0) {
            return null;
        }
        return difference().multiply(BigDecimal.valueOf(100)).divide(oldCost, 0, RoundingMode.HALF_UP);
    }
}
