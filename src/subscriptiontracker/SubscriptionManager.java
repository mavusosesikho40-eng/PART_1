package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Holds the list of subscriptions and answers questions about them.
 */
public class SubscriptionManager {

    /** How the monthly total compares with the monthly budget. */
    public enum BudgetStatus {
        /** No budget has been set. */
        NO_BUDGET,
        /** Below {@link #NEAR_BUDGET_SHARE} of the budget. */
        UNDER,
        /** At least {@link #NEAR_BUDGET_SHARE} of the budget, but not over it. */
        NEAR,
        /** More than the budget. */
        OVER
    }

    /** A price change together with the subscription it belongs to. */
    public record PriceChangeEntry(Subscription subscription, PriceChange change) {
    }

    /** Spending at or above this share of the budget counts as near it. */
    public static final BigDecimal NEAR_BUDGET_SHARE = new BigDecimal("0.90");

    private final List<Subscription> subscriptions = new ArrayList<>();
    private int nextId = 1;
    private BigDecimal monthlyBudget;
    private String currencySymbol = "";

    /** The symbol amounts are shown with, e.g. "R" or "$"; empty for none. */
    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String symbol) {
        currencySymbol = symbol == null ? "" : symbol.strip();
    }

    public Optional<BigDecimal> getMonthlyBudget() {
        return Optional.ofNullable(monthlyBudget);
    }

    /** Sets the monthly budget; null, zero or a negative amount removes it. */
    public void setMonthlyBudget(BigDecimal budget) {
        monthlyBudget = budget == null || budget.signum() <= 0 ? null : budget;
    }

    public BudgetStatus getBudgetStatus() {
        if (monthlyBudget == null) {
            return BudgetStatus.NO_BUDGET;
        }
        BigDecimal monthly = getMonthlyTotal();
        if (monthly.compareTo(monthlyBudget) > 0) {
            return BudgetStatus.OVER;
        }
        if (monthly.compareTo(monthlyBudget.multiply(NEAR_BUDGET_SHARE)) >= 0) {
            return BudgetStatus.NEAR;
        }
        return BudgetStatus.UNDER;
    }

    public Subscription add(String name, BigDecimal cost, BillingCycle cycle,
            LocalDate nextPayment, String category) {
        Subscription sub = new Subscription(nextId++, name, cost, cycle, nextPayment, category);
        subscriptions.add(sub);
        return sub;
    }

    /** Adds a subscription loaded from storage, keeping its existing id. */
    public void restore(Subscription sub) {
        subscriptions.add(sub);
        nextId = Math.max(nextId, sub.getId() + 1);
    }

    public boolean remove(int id) {
        return subscriptions.removeIf(s -> s.getId() == id);
    }

    public Optional<Subscription> find(int id) {
        return subscriptions.stream().filter(s -> s.getId() == id).findFirst();
    }

    /** True when there are no active subscriptions (cancelled ones don't count). */
    public boolean isEmpty() {
        return active().isEmpty();
    }

    /** Active subscriptions, soonest payment first. Cancelled ones are left out. */
    public List<Subscription> getAll() {
        return getSortedByNextPayment(true);
    }

    /** Every subscription, active and cancelled, soonest payment first; used for saving. */
    public List<Subscription> getAllIncludingCancelled() {
        List<Subscription> sorted = new ArrayList<>(subscriptions);
        sorted.sort(Comparator.comparing(Subscription::getNextPayment)
                .thenComparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /** Cancelled subscriptions, most recently cancelled first. */
    public List<Subscription> getCancelled() {
        List<Subscription> cancelled = new ArrayList<>(subscriptions.stream().filter(Subscription::isCancelled).toList());
        cancelled.sort(Comparator.comparing(Subscription::getCancelledOn).reversed()
                .thenComparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER));
        return cancelled;
    }

    /** Total saved so far by all cancelled subscriptions. */
    public BigDecimal getSavedSoFar(LocalDate today) {
        return subscriptions.stream().map(s -> s.getSavedSoFar(today))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What the cancelled subscriptions would cost per month if they were still active. */
    public BigDecimal getCancelledMonthlyTotal() {
        return getCancelled().stream().map(Subscription::getMonthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Subscription> active() {
        return subscriptions.stream().filter(s -> !s.isCancelled()).toList();
    }

    /**
     * All subscriptions ordered by next payment date, soonest or latest
     * first. Payments on the same day are ordered by name.
     */
    public List<Subscription> getSortedByNextPayment(boolean soonestFirst) {
        Comparator<Subscription> byDate = Comparator.comparing(Subscription::getNextPayment);
        if (!soonestFirst) {
            byDate = byDate.reversed();
        }
        List<Subscription> sorted = new ArrayList<>(active());
        sorted.sort(byDate.thenComparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /** Subscriptions due between today and today + days (inclusive). */
    public List<Subscription> getUpcoming(LocalDate today, int days) {
        LocalDate end = today.plusDays(days);
        return getAll().stream()
                .filter(s -> !s.getNextPayment().isBefore(today) && !s.getNextPayment().isAfter(end))
                .toList();
    }

    /**
     * Subscriptions whose category contains the query, ignoring case,
     * soonest payment first.
     */
    public List<Subscription> searchByCategory(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return getAll().stream()
                .filter(s -> s.getCategory().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /**
     * All subscriptions ordered by what they cost per month, so different
     * billing cycles compare fairly. Equal costs are ordered by name.
     */
    public List<Subscription> getSortedByCost(boolean highestFirst) {
        Comparator<Subscription> byCost = Comparator.comparing(Subscription::getMonthlyCost);
        if (highestFirst) {
            byCost = byCost.reversed();
        }
        List<Subscription> sorted = new ArrayList<>(active());
        sorted.sort(byCost.thenComparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * All subscriptions in alphabetical order by name, ignoring case.
     * Subscriptions with the same name are ordered by next payment date.
     */
    public List<Subscription> getSortedByName(boolean aToZ) {
        Comparator<Subscription> byName = Comparator.comparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER);
        if (!aToZ) {
            byName = byName.reversed();
        }
        List<Subscription> sorted = new ArrayList<>(active());
        sorted.sort(byName.thenComparing(Subscription::getNextPayment));
        return sorted;
    }

    /**
     * All subscriptions in alphabetical order by category, ignoring case.
     * Within a category they are ordered by name, A to Z.
     */
    public List<Subscription> getSortedByCategory(boolean aToZ) {
        Comparator<Subscription> byCategory =
                Comparator.comparing(Subscription::getCategory, String.CASE_INSENSITIVE_ORDER);
        if (!aToZ) {
            byCategory = byCategory.reversed();
        }
        List<Subscription> sorted = new ArrayList<>(active());
        sorted.sort(byCategory.thenComparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * All subscriptions ordered by billing cycle, shortest (weekly) or
     * longest (yearly) first. Within a cycle they are ordered by name, A to Z.
     */
    public List<Subscription> getSortedByCycle(boolean shortestFirst) {
        // BillingCycle constants are declared from shortest to longest.
        Comparator<Subscription> byCycle = Comparator.comparing(Subscription::getCycle);
        if (!shortestFirst) {
            byCycle = byCycle.reversed();
        }
        List<Subscription> sorted = new ArrayList<>(active());
        sorted.sort(byCycle.thenComparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * All subscriptions ordered by ID. IDs are given out in the order
     * subscriptions are added, so lowest first means oldest first.
     */
    public List<Subscription> getSortedById(boolean lowestFirst) {
        Comparator<Subscription> byId = Comparator.comparingInt(Subscription::getId);
        if (!lowestFirst) {
            byId = byId.reversed();
        }
        List<Subscription> sorted = new ArrayList<>(active());
        sorted.sort(byId);
        return sorted;
    }

    /** Active subscriptions with the given billing cycle, soonest payment first. */
    public List<Subscription> getByCycle(BillingCycle cycle) {
        return getAll().stream().filter(s -> s.getCycle() == cycle).toList();
    }

    /** Free trials, soonest ending first. */
    public List<Subscription> getFreeTrials() {
        return getAll().stream().filter(Subscription::isFreeTrial).toList();
    }

    /** Free trials ending between today and today + days (inclusive), soonest first. */
    public List<Subscription> getTrialsEndingWithin(LocalDate today, int days) {
        return getUpcoming(today, days).stream().filter(Subscription::isFreeTrial).toList();
    }

    /** Price changes of active subscriptions, newest first. */
    public List<PriceChangeEntry> getPriceChanges() {
        List<PriceChangeEntry> entries = new ArrayList<>();
        for (Subscription s : active()) {
            for (PriceChange change : s.getPriceChanges()) {
                entries.add(new PriceChangeEntry(s, change));
            }
        }
        entries.sort(Comparator.comparing((PriceChangeEntry e) -> e.change().date()).reversed()
                .thenComparing(e -> e.subscription().getName(), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    /**
     * How much price changes since the given day have added to monthly
     * spending on active subscriptions (negative if prices went down).
     * New subscriptions don't count; only changes to existing prices do.
     */
    public BigDecimal getMonthlyPriceChangeSince(LocalDate since) {
        BigDecimal total = BigDecimal.ZERO;
        for (Subscription s : active()) {
            BigDecimal then = s.getCycle().toMonthly(s.getCostOn(since));
            total = total.add(s.getMonthlyCost().subtract(then));
        }
        return total;
    }

    public BigDecimal getMonthlyTotal() {
        return active().stream().map(Subscription::getMonthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getYearlyTotal() {
        return active().stream().map(Subscription::getYearlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Monthly cost per category, sorted by category name. */
    public Map<String, BigDecimal> getMonthlyByCategory() {
        Map<String, BigDecimal> totals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Subscription s : active()) {
            totals.merge(s.getCategory(), s.getMonthlyCost(), BigDecimal::add);
        }
        return totals;
    }

    /**
     * Rolls every overdue payment date forward to its next occurrence.
     *
     * @return how many subscriptions were updated
     */
    public int rollForwardAll(LocalDate today) {
        int count = 0;
        for (Subscription s : active()) {
            if (s.rollForward(today)) {
                count++;
            }
        }
        return count;
    }
}
