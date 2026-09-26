package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        List<Subscription> sorted = new ArrayList<>(subscriptions);
        sorted.sort(Comparator.comparing(Subscription::getNextPayment)
                .thenComparing(Subscription::getName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /** Subscriptions due between today and today + days (inclusive). */
    public List<Subscription> getUpcoming(LocalDate today, int days) {
        LocalDate end = today.plusDays(days);
        return getAll().stream()
                .filter(s -> !s.getNextPayment().isBefore(today) && !s.getNextPayment().isAfter(end))
                .toList();
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
