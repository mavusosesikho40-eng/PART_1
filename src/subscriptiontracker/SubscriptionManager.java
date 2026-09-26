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

    private final List<Subscription> subscriptions = new ArrayList<>();
    private int nextId = 1;

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

    public boolean isEmpty() {
        return subscriptions.isEmpty();
    }

    /** All subscriptions, soonest payment first. */
    public List<Subscription> getAll() {
        return getSortedByNextPayment(true);
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
        List<Subscription> sorted = new ArrayList<>(subscriptions);
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
        List<Subscription> sorted = new ArrayList<>(subscriptions);
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
        List<Subscription> sorted = new ArrayList<>(subscriptions);
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
        List<Subscription> sorted = new ArrayList<>(subscriptions);
        sorted.sort(byCategory.thenComparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    public BigDecimal getMonthlyTotal() {
        return subscriptions.stream().map(Subscription::getMonthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getYearlyTotal() {
        return subscriptions.stream().map(Subscription::getYearlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Monthly cost per category, sorted by category name. */
    public Map<String, BigDecimal> getMonthlyByCategory() {
        Map<String, BigDecimal> totals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Subscription s : subscriptions) {
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
        for (Subscription s : subscriptions) {
            if (s.rollForward(today)) {
                count++;
            }
        }
        return count;
    }
}
