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
    public void freeTrialEndsOnceItsEndDateHasPassed() {
        Subscription sub = monthly(TODAY.minusDays(1));
        sub.setFreeTrial(true);

        sub.rollForward(TODAY);

        assertFalse(sub.isFreeTrial());
        assertEquals(TODAY.minusDays(1).plusMonths(1), sub.getNextPayment());
    }

    @Test
    public void freeTrialEndingTodayOrLaterIsStillATrial() {
        Subscription today = monthly(TODAY);
        today.setFreeTrial(true);
        Subscription later = monthly(TODAY.plusDays(5));
        later.setFreeTrial(true);

        today.rollForward(TODAY);
        later.rollForward(TODAY);

        assertTrue(today.isFreeTrial());
        assertTrue(later.isFreeTrial());
    }

    @Test
    public void savedSoFarCountsThePaymentsSkippedSinceCancelling() {
        Subscription sub = monthly(LocalDate.of(2026, 7, 1));
        sub.cancel(LocalDate.of(2026, 6, 20));

        // Payments skipped on 1 July, 1 August and 1 September.
        assertEquals(new BigDecimal("597.00"), sub.getSavedSoFar(TODAY));
        assertEquals(0, sub.getSavedSoFar(LocalDate.of(2026, 6, 30)).signum());
    }

    @Test
    public void activeSubscriptionHasSavedNothing() {
        assertEquals(BigDecimal.ZERO, monthly(TODAY.minusDays(40)).getSavedSoFar(TODAY));
    }

    @Test
    public void cancelledSubscriptionIsNotRolledForward() {
        Subscription sub = monthly(TODAY.minusDays(40));
        sub.cancel(TODAY.minusDays(45));

        assertFalse(sub.rollForward(TODAY));
        assertEquals(TODAY.minusDays(40), sub.getNextPayment());
    }

    @Test
    public void reactivatingClearsTheCancellationAndMovesTheDateForward() {
        Subscription sub = monthly(LocalDate.of(2026, 7, 1));
        sub.cancel(LocalDate.of(2026, 6, 20));

        sub.reactivate(TODAY);

        assertFalse(sub.isCancelled());
        assertEquals(LocalDate.of(2026, 10, 1), sub.getNextPayment());
    }

    @Test
    public void monthlyAndYearlyCostsFollowBillingCycle() {
        Subscription sub = new Subscription(1, "Adobe", new BigDecimal("2400.00"),
                BillingCycle.YEARLY, TODAY, "Software");

        assertEquals(new BigDecimal("200.00"), sub.getMonthlyCost());
        assertEquals(0, new BigDecimal("2400").compareTo(sub.getYearlyCost()));
    }
}
