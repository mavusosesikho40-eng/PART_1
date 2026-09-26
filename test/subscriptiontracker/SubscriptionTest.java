package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubscriptionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private static Subscription monthly(LocalDate nextPayment) {
        return new Subscription(1, "Netflix", new BigDecimal("199.00"),
                BillingCycle.MONTHLY, nextPayment, "Streaming");
    }

    @Test
    public void rollForwardMovesOverduePaymentToNextOccurrence() {
        Subscription sub = monthly(LocalDate.of(2026, 9, 1));

        assertTrue(sub.rollForward(TODAY));
        assertEquals(LocalDate.of(2026, 10, 1), sub.getNextPayment());
    }

    @Test
    public void rollForwardSkipsSeveralMissedPayments() {
        Subscription sub = new Subscription(1, "Gym", new BigDecimal("50"),
                BillingCycle.WEEKLY, TODAY.minusWeeks(5).minusDays(1), "Health");

        assertTrue(sub.rollForward(TODAY));
        assertEquals(TODAY.plusDays(6), sub.getNextPayment());
    }

    @Test
    public void rollForwardLeavesPaymentDueTodayAlone() {
        Subscription sub = monthly(TODAY);

        assertFalse(sub.rollForward(TODAY));
        assertEquals(TODAY, sub.getNextPayment());
    }

    @Test
    public void rollForwardLeavesFuturePaymentAlone() {
        Subscription sub = monthly(TODAY.plusDays(3));

        assertFalse(sub.rollForward(TODAY));
        assertEquals(TODAY.plusDays(3), sub.getNextPayment());
    }

    @Test
    public void monthlyAndYearlyCostsFollowBillingCycle() {
        Subscription sub = new Subscription(1, "Adobe", new BigDecimal("2400.00"),
                BillingCycle.YEARLY, TODAY, "Software");

        assertEquals(new BigDecimal("200.00"), sub.getMonthlyCost());
        assertEquals(0, new BigDecimal("2400").compareTo(sub.getYearlyCost()));
    }
}
