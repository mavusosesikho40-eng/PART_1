package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubscriptionManagerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private SubscriptionManager manager;

    @Before
    public void setUp() {
        manager = new SubscriptionManager();
    }

    private Subscription add(String name, String cost, BillingCycle cycle, LocalDate next, String category) {
        return manager.add(name, new BigDecimal(cost), cycle, next, category);
    }

    private static List<String> names(List<Subscription> subs) {
        return subs.stream().map(Subscription::getName).toList();
    }

    @Test
    public void startsEmpty() {
        assertTrue(manager.isEmpty());
        assertTrue(manager.getAll().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(manager.getMonthlyTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(manager.getYearlyTotal()));
    }

    @Test
    public void addAssignsIncreasingIds() {
        assertEquals(1, add("A", "1", BillingCycle.MONTHLY, TODAY, "X").getId());
        assertEquals(2, add("B", "1", BillingCycle.MONTHLY, TODAY, "X").getId());
        assertFalse(manager.isEmpty());
    }

    @Test
    public void findAndRemoveById() {
        Subscription netflix = add("Netflix", "199", BillingCycle.MONTHLY, TODAY, "Streaming");

        assertEquals(netflix, manager.find(netflix.getId()).orElseThrow());
        assertTrue(manager.find(99).isEmpty());

        assertTrue(manager.remove(netflix.getId()));
        assertFalse(manager.remove(netflix.getId()));
        assertTrue(manager.find(netflix.getId()).isEmpty());
    }

    @Test
    public void idsAreNotReusedAfterRemove() {
        add("A", "1", BillingCycle.MONTHLY, TODAY, "X");
        Subscription b = add("B", "1", BillingCycle.MONTHLY, TODAY, "X");
        manager.remove(b.getId());

        assertEquals(3, add("C", "1", BillingCycle.MONTHLY, TODAY, "X").getId());
    }

    @Test
    public void restoreKeepsIdAndMovesNextIdPastIt() {
        manager.restore(new Subscription(7, "Old", BigDecimal.ONE, BillingCycle.MONTHLY, TODAY, "X"));

        assertEquals("Old", manager.find(7).orElseThrow().getName());
        assertEquals(8, add("New", "1", BillingCycle.MONTHLY, TODAY, "X").getId());
    }

    @Test
    public void getAllSortsBySoonestPaymentThenName() {
        add("zeta", "1", BillingCycle.MONTHLY, TODAY.plusDays(5), "X");
        add("Beta", "1", BillingCycle.MONTHLY, TODAY.plusDays(5), "X");
        add("alpha", "1", BillingCycle.MONTHLY, TODAY.plusDays(9), "X");
        add("Gamma", "1", BillingCycle.MONTHLY, TODAY.plusDays(1), "X");

        assertEquals(List.of("Gamma", "Beta", "zeta", "alpha"), names(manager.getAll()));
    }

    @Test
    public void getUpcomingIncludesBothEndsOfTheWindow() {
        add("Yesterday", "1", BillingCycle.MONTHLY, TODAY.minusDays(1), "X");
        add("Today", "1", BillingCycle.MONTHLY, TODAY, "X");
        add("Edge", "1", BillingCycle.MONTHLY, TODAY.plusDays(7), "X");
        add("TooLate", "1", BillingCycle.MONTHLY, TODAY.plusDays(8), "X");

        assertEquals(List.of("Today", "Edge"), names(manager.getUpcoming(TODAY, 7)));
    }

    @Test
    public void totalsCombineDifferentBillingCycles() {
        add("Netflix", "199.00", BillingCycle.MONTHLY, TODAY, "Streaming");
        add("Spotify", "59.99", BillingCycle.MONTHLY, TODAY, "Music");
        add("Adobe", "2400.00", BillingCycle.YEARLY, TODAY, "Software");

        assertEquals(new BigDecimal("458.99"), manager.getMonthlyTotal());
        assertEquals(0, new BigDecimal("5507.88").compareTo(manager.getYearlyTotal()));
    }

    @Test
    public void monthlyByCategoryGroupsIgnoringCase() {
        add("Netflix", "199.00", BillingCycle.MONTHLY, TODAY, "Streaming");
        add("Showmax", "99.00", BillingCycle.MONTHLY, TODAY, "streaming");
        add("Spotify", "59.99", BillingCycle.MONTHLY, TODAY, "Music");

        Map<String, BigDecimal> byCategory = manager.getMonthlyByCategory();

        assertEquals(2, byCategory.size());
        assertEquals(new BigDecimal("298.00"), byCategory.get("Streaming"));
        assertEquals(new BigDecimal("59.99"), byCategory.get("Music"));
        assertEquals(List.of("Music", "Streaming"), List.copyOf(byCategory.keySet()));
    }

    @Test
    public void searchByCategoryMatchesPartOfTheNameIgnoringCase() {
        add("Netflix", "199", BillingCycle.MONTHLY, TODAY.plusDays(5), "Streaming");
        add("Showmax", "99", BillingCycle.MONTHLY, TODAY.plusDays(2), "Video streaming");
        add("Spotify", "59.99", BillingCycle.MONTHLY, TODAY.plusDays(1), "Music");

        assertEquals(List.of("Showmax", "Netflix"), names(manager.searchByCategory("STREAM")));
        assertEquals(List.of("Spotify"), names(manager.searchByCategory("  music ")));
        assertTrue(manager.searchByCategory("Gaming").isEmpty());
    }

    @Test
    public void sortByCostComparesMonthlyEquivalents() {
        add("Adobe", "2400.00", BillingCycle.YEARLY, TODAY, "Software");   // 200.00 a month
        add("Gym", "250.00", BillingCycle.MONTHLY, TODAY, "Health");       // 250.00 a month
        add("Spotify", "59.99", BillingCycle.MONTHLY, TODAY, "Music");
        add("Coffee", "20.00", BillingCycle.WEEKLY, TODAY, "Food");        // 86.67 a month

        assertEquals(List.of("Gym", "Adobe", "Coffee", "Spotify"), names(manager.getSortedByCost(true)));
        assertEquals(List.of("Spotify", "Coffee", "Adobe", "Gym"), names(manager.getSortedByCost(false)));
    }

    @Test
    public void sortByCostOrdersEqualCostsByName() {
        add("netflix", "199", BillingCycle.MONTHLY, TODAY, "Streaming");
        add("Apple TV", "199", BillingCycle.MONTHLY, TODAY, "Streaming");

        assertEquals(List.of("Apple TV", "netflix"), names(manager.getSortedByCost(true)));
        assertEquals(List.of("Apple TV", "netflix"), names(manager.getSortedByCost(false)));
    }

    @Test
    public void sortByNextPaymentInEitherDirection() {
        add("Later", "1", BillingCycle.MONTHLY, TODAY.plusDays(30), "X");
        add("Soon", "1", BillingCycle.MONTHLY, TODAY.plusDays(1), "X");
        add("beta", "1", BillingCycle.MONTHLY, TODAY.plusDays(10), "X");
        add("Alpha", "1", BillingCycle.MONTHLY, TODAY.plusDays(10), "X");

        assertEquals(List.of("Soon", "Alpha", "beta", "Later"), names(manager.getSortedByNextPayment(true)));
        assertEquals(List.of("Later", "Alpha", "beta", "Soon"), names(manager.getSortedByNextPayment(false)));
    }

    @Test
    public void sortByNameIgnoresCaseInEitherDirection() {
        add("spotify", "1", BillingCycle.MONTHLY, TODAY.plusDays(1), "X");
        add("Adobe", "1", BillingCycle.MONTHLY, TODAY.plusDays(2), "X");
        add("Netflix", "1", BillingCycle.MONTHLY, TODAY.plusDays(3), "X");
        add("apple TV", "1", BillingCycle.MONTHLY, TODAY.plusDays(4), "X");

        assertEquals(List.of("Adobe", "apple TV", "Netflix", "spotify"), names(manager.getSortedByName(true)));
        assertEquals(List.of("spotify", "Netflix", "apple TV", "Adobe"), names(manager.getSortedByName(false)));
    }

    @Test
    public void sortByNameOrdersSameNameBySoonestPayment() {
        Subscription later = add("Gym", "1", BillingCycle.MONTHLY, TODAY.plusDays(20), "X");
        Subscription sooner = add("gym", "1", BillingCycle.MONTHLY, TODAY.plusDays(5), "X");

        assertEquals(List.of(sooner, later), manager.getSortedByName(true));
        assertEquals(List.of(sooner, later), manager.getSortedByName(false));
    }

    @Test
    public void sortByCategoryIgnoresCaseInEitherDirection() {
        add("Spotify", "1", BillingCycle.MONTHLY, TODAY, "Music");
        add("Adobe", "1", BillingCycle.MONTHLY, TODAY, "software");
        add("Gym", "1", BillingCycle.MONTHLY, TODAY, "Health");

        assertEquals(List.of("Gym", "Spotify", "Adobe"), names(manager.getSortedByCategory(true)));
        assertEquals(List.of("Adobe", "Spotify", "Gym"), names(manager.getSortedByCategory(false)));
    }

    @Test
    public void sortByCategoryOrdersByNameWithinACategory() {
        add("Showmax", "1", BillingCycle.MONTHLY, TODAY, "Streaming");
        add("apple TV", "1", BillingCycle.MONTHLY, TODAY, "streaming");
        add("Netflix", "1", BillingCycle.MONTHLY, TODAY, "Streaming");
        add("Spotify", "1", BillingCycle.MONTHLY, TODAY, "Music");

        assertEquals(List.of("Spotify", "apple TV", "Netflix", "Showmax"), names(manager.getSortedByCategory(true)));
        assertEquals(List.of("apple TV", "Netflix", "Showmax", "Spotify"), names(manager.getSortedByCategory(false)));
    }

    @Test
    public void sortByCycleGoesFromWeeklyToYearly() {
        add("Adobe", "1", BillingCycle.YEARLY, TODAY, "X");
        add("Spotify", "1", BillingCycle.MONTHLY, TODAY, "X");
        add("Coffee", "1", BillingCycle.WEEKLY, TODAY, "X");
        add("Insurance", "1", BillingCycle.QUARTERLY, TODAY, "X");

        assertEquals(List.of("Coffee", "Spotify", "Insurance", "Adobe"), names(manager.getSortedByCycle(true)));
        assertEquals(List.of("Adobe", "Insurance", "Spotify", "Coffee"), names(manager.getSortedByCycle(false)));
    }

    @Test
    public void sortByCycleOrdersByNameWithinACycle() {
        add("Spotify", "1", BillingCycle.MONTHLY, TODAY, "X");
        add("apple TV", "1", BillingCycle.MONTHLY, TODAY, "X");
        add("Netflix", "1", BillingCycle.MONTHLY, TODAY, "X");
        add("Adobe", "1", BillingCycle.YEARLY, TODAY, "X");

        assertEquals(List.of("apple TV", "Netflix", "Spotify", "Adobe"), names(manager.getSortedByCycle(true)));
        assertEquals(List.of("Adobe", "apple TV", "Netflix", "Spotify"), names(manager.getSortedByCycle(false)));
    }

    @Test
    public void sortByIdInEitherDirection() {
        manager.restore(new Subscription(10, "Ten", BigDecimal.ONE, BillingCycle.MONTHLY, TODAY, "X"));
        manager.restore(new Subscription(2, "Two", BigDecimal.ONE, BillingCycle.MONTHLY, TODAY.plusDays(9), "X"));
        manager.restore(new Subscription(7, "Seven", BigDecimal.ONE, BillingCycle.MONTHLY, TODAY.plusDays(1), "X"));

        assertEquals(List.of("Two", "Seven", "Ten"), names(manager.getSortedById(true)));
        assertEquals(List.of("Ten", "Seven", "Two"), names(manager.getSortedById(false)));
    }

    @Test
    public void thereIsNoBudgetByDefault() {
        assertTrue(manager.getMonthlyBudget().isEmpty());
        assertEquals(SubscriptionManager.BudgetStatus.NO_BUDGET, manager.getBudgetStatus());
    }

    @Test
    public void budgetStatusChangesAtNinetyPercentAndAboveTheBudget() {
        manager.setMonthlyBudget(new BigDecimal("100.00"));
        Subscription sub = add("Gym", "89.99", BillingCycle.MONTHLY, TODAY, "Health");
        assertEquals(SubscriptionManager.BudgetStatus.UNDER, manager.getBudgetStatus());

        sub.setCost(new BigDecimal("90.00"));
        assertEquals(SubscriptionManager.BudgetStatus.NEAR, manager.getBudgetStatus());

        sub.setCost(new BigDecimal("100.00"));
        assertEquals(SubscriptionManager.BudgetStatus.NEAR, manager.getBudgetStatus());

        sub.setCost(new BigDecimal("100.01"));
        assertEquals(SubscriptionManager.BudgetStatus.OVER, manager.getBudgetStatus());
    }

    @Test
    public void budgetComparesMonthlyEquivalents() {
        manager.setMonthlyBudget(new BigDecimal("150"));
        add("Adobe", "2400.00", BillingCycle.YEARLY, TODAY, "Software");   // 200.00 a month

        assertEquals(SubscriptionManager.BudgetStatus.OVER, manager.getBudgetStatus());
    }

    @Test
    public void zeroNegativeOrNullBudgetRemovesIt() {
        manager.setMonthlyBudget(new BigDecimal("500"));
        assertEquals(new BigDecimal("500"), manager.getMonthlyBudget().orElseThrow());

        manager.setMonthlyBudget(BigDecimal.ZERO);
        assertTrue(manager.getMonthlyBudget().isEmpty());

        manager.setMonthlyBudget(new BigDecimal("500"));
        manager.setMonthlyBudget(new BigDecimal("-1"));
        assertTrue(manager.getMonthlyBudget().isEmpty());

        manager.setMonthlyBudget(new BigDecimal("500"));
        manager.setMonthlyBudget(null);
        assertEquals(SubscriptionManager.BudgetStatus.NO_BUDGET, manager.getBudgetStatus());
    }

    @Test
    public void freeTrialsAreListedSoonestEndingFirst() {
        add("Paid", "1", BillingCycle.MONTHLY, TODAY.plusDays(1), "X");
        add("Later", "1", BillingCycle.MONTHLY, TODAY.plusDays(20), "X").setFreeTrial(true);
        add("Sooner", "1", BillingCycle.MONTHLY, TODAY.plusDays(3), "X").setFreeTrial(true);

        assertEquals(List.of("Sooner", "Later"), names(manager.getFreeTrials()));
    }

    @Test
    public void trialsEndingWithinIncludesOnlyTrialsInTheWindow() {
        add("Paid", "1", BillingCycle.MONTHLY, TODAY.plusDays(1), "X");
        add("Today", "1", BillingCycle.MONTHLY, TODAY, "X").setFreeTrial(true);
        add("Edge", "1", BillingCycle.MONTHLY, TODAY.plusDays(7), "X").setFreeTrial(true);
        add("TooLate", "1", BillingCycle.MONTHLY, TODAY.plusDays(8), "X").setFreeTrial(true);

        assertEquals(List.of("Today", "Edge"), names(manager.getTrialsEndingWithin(TODAY, 7)));
    }

    @Test
    public void rollForwardAllCountsOnlyChangedSubscriptions() {
        add("Overdue1", "1", BillingCycle.MONTHLY, TODAY.minusDays(3), "X");
        add("Overdue2", "1", BillingCycle.WEEKLY, TODAY.minusDays(10), "X");
        add("Future", "1", BillingCycle.MONTHLY, TODAY.plusDays(3), "X");

        assertEquals(2, manager.rollForwardAll(TODAY));
        assertTrue(manager.getAll().stream().noneMatch(s -> s.getNextPayment().isBefore(TODAY)));
        assertEquals(0, manager.rollForwardAll(TODAY));
    }
}
