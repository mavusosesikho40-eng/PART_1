package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    public void changePriceRecordsTheOldAndNewCost() {
        Subscription sub = monthly(TODAY);

        sub.changePrice(new BigDecimal("229.00"), TODAY);

        assertEquals(new BigDecimal("229.00"), sub.getCost());
        assertEquals(List.of(new PriceChange(TODAY, new BigDecimal("199.00"), new BigDecimal("229.00"))),
                sub.getPriceChanges());
    }

    @Test
    public void sameCostIsNotRecordedAsAChange() {
        Subscription sub = monthly(TODAY);

        sub.changePrice(new BigDecimal("199"), TODAY);

        assertTrue(sub.getPriceChanges().isEmpty());
    }

    @Test
    public void costOnADayUsesThePriceHistory() {
        Subscription sub = monthly(TODAY);
        sub.changePrice(new BigDecimal("229.00"), LocalDate.of(2026, 3, 1));
        sub.changePrice(new BigDecimal("249.00"), LocalDate.of(2026, 8, 1));

        assertEquals(new BigDecimal("199.00"), sub.getCostOn(LocalDate.of(2026, 1, 1)));
        assertEquals(new BigDecimal("229.00"), sub.getCostOn(LocalDate.of(2026, 3, 1)));
        assertEquals(new BigDecimal("229.00"), sub.getCostOn(LocalDate.of(2026, 7, 31)));
        assertEquals(new BigDecimal("249.00"), sub.getCostOn(TODAY));
    }

    @Test
    public void priceChangeDifferenceAndPercent() {
        PriceChange rise = new PriceChange(TODAY, new BigDecimal("169.00"), new BigDecimal("199.00"));
        PriceChange drop = new PriceChange(TODAY, new BigDecimal("200.00"), new BigDecimal("150.00"));
        PriceChange fromFree = new PriceChange(TODAY, new BigDecimal("0.00"), new BigDecimal("99.00"));

        assertEquals(new BigDecimal("30.00"), rise.difference());
        assertEquals(new BigDecimal("18"), rise.percent());
        assertEquals(new BigDecimal("-25"), drop.percent());
        assertEquals(null, fromFree.percent());
    }

    @Test
    public void noteIsTrimmedAndEmptyByDefault() {
        Subscription sub = monthly(TODAY);
        assertFalse(sub.hasNote());
        assertEquals("", sub.getNote());

        sub.setNote("  family plan  ");
        assertTrue(sub.hasNote());
        assertEquals("family plan", sub.getNote());

        sub.setNote(null);
        assertFalse(sub.hasNote());
    }

    @Test
    public void billingOnThe31stGoesBackToThe31stAfterAShortMonth() {
        Subscription sub = new Subscription(1, "Gym", new BigDecimal("250"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 1, 31), "Health");

        sub.rollForward(LocalDate.of(2026, 2, 10));
        assertEquals(LocalDate.of(2026, 2, 28), sub.getNextPayment());

        sub.rollForward(LocalDate.of(2026, 3, 10));
        assertEquals(LocalDate.of(2026, 3, 31), sub.getNextPayment());

        sub.rollForward(LocalDate.of(2026, 5, 15));
        assertEquals(LocalDate.of(2026, 5, 31), sub.getNextPayment());
        assertEquals(31, sub.getBillingDay());
    }

    @Test
    public void quarterlyAndYearlyKeepTheirBillingDayToo() {
        Subscription quarterly = new Subscription(1, "Insurance", BigDecimal.TEN, BillingCycle.QUARTERLY,
                LocalDate.of(2025, 11, 30), "Health");
        quarterly.rollForward(LocalDate.of(2026, 3, 1));
        assertEquals(LocalDate.of(2026, 5, 30), quarterly.getNextPayment());

        Subscription leapYear = new Subscription(2, "Domain", BigDecimal.TEN, BillingCycle.YEARLY,
                LocalDate.of(2028, 2, 29), "Software");
        leapYear.rollForward(LocalDate.of(2029, 3, 1));
        assertEquals(LocalDate.of(2030, 2, 28), leapYear.getNextPayment());
        leapYear.rollForward(LocalDate.of(2032, 2, 1));
        assertEquals(LocalDate.of(2032, 2, 29), leapYear.getNextPayment());
    }

    @Test
    public void settingANewDateChangesTheBillingDayButKeepingTheSameDateDoesNot() {
        Subscription sub = new Subscription(1, "Gym", BigDecimal.TEN, BillingCycle.MONTHLY,
                LocalDate.of(2026, 1, 31), "Health");
        sub.rollForward(LocalDate.of(2026, 2, 10));

        sub.setNextPayment(sub.getNextPayment());
        assertEquals(31, sub.getBillingDay());

        sub.setNextPayment(LocalDate.of(2026, 3, 15));
        assertEquals(15, sub.getBillingDay());
    }

    @Test
    public void savedSoFarFollowsTheBillingDay() {
        Subscription sub = new Subscription(1, "Gym", new BigDecimal("100"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 1, 31), "Health");
        sub.cancel(LocalDate.of(2026, 1, 20));

        // Payments skipped on 31 Jan, 28 Feb and 31 Mar.
        assertEquals(new BigDecimal("300"), sub.getSavedSoFar(LocalDate.of(2026, 3, 31)));
    }

    @Test
    public void monthlyAndYearlyCostsFollowBillingCycle() {
        Subscription sub = new Subscription(1, "Adobe", new BigDecimal("2400.00"),
                BillingCycle.YEARLY, TODAY, "Software");

        assertEquals(new BigDecimal("200.00"), sub.getMonthlyCost());
        assertEquals(0, new BigDecimal("2400").compareTo(sub.getYearlyCost()));
    }
}
