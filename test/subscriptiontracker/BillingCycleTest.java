package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BillingCycleTest {

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals("expected " + expected + " but was " + actual,
                0, new BigDecimal(expected).compareTo(actual));
    }

    @Test
    public void toYearlyMultipliesByPaymentsPerYear() {
        assertAmount("520", BillingCycle.WEEKLY.toYearly(new BigDecimal("10")));
        assertAmount("1200", BillingCycle.MONTHLY.toYearly(new BigDecimal("100")));
        assertAmount("120", BillingCycle.QUARTERLY.toYearly(new BigDecimal("30")));
        assertAmount("1200", BillingCycle.YEARLY.toYearly(new BigDecimal("1200")));
    }

    @Test
    public void toMonthlySpreadsYearlyCostOverTwelveMonths() {
        assertAmount("100.00", BillingCycle.YEARLY.toMonthly(new BigDecimal("1200")));
        assertAmount("99.99", BillingCycle.MONTHLY.toMonthly(new BigDecimal("99.99")));
        assertAmount("10.00", BillingCycle.QUARTERLY.toMonthly(new BigDecimal("30")));
    }

    @Test
    public void toMonthlyRoundsToTwoDecimals() {
        // 10 * 52 / 12 = 43.333...
        BigDecimal monthly = BillingCycle.WEEKLY.toMonthly(new BigDecimal("10"));
        assertEquals(new BigDecimal("43.33"), monthly);
    }

    @Test
    public void nextAdvancesByOneCycle() {
        LocalDate start = LocalDate.of(2026, 3, 15);
        assertEquals(LocalDate.of(2026, 3, 22), BillingCycle.WEEKLY.next(start));
        assertEquals(LocalDate.of(2026, 4, 15), BillingCycle.MONTHLY.next(start));
        assertEquals(LocalDate.of(2026, 6, 15), BillingCycle.QUARTERLY.next(start));
        assertEquals(LocalDate.of(2027, 3, 15), BillingCycle.YEARLY.next(start));
    }

    @Test
    public void nextClampsToEndOfShorterMonth() {
        assertEquals(LocalDate.of(2026, 2, 28), BillingCycle.MONTHLY.next(LocalDate.of(2026, 1, 31)));
        assertEquals(LocalDate.of(2029, 2, 28), BillingCycle.YEARLY.next(LocalDate.of(2028, 2, 29)));
    }
}
