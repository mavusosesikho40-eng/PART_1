package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single subscription the user pays for.
 */
public class Subscription {

    private final int id;
    private String name;
    private BigDecimal cost;
    private BillingCycle cycle;
    private LocalDate nextPayment;
    private String category;

    public Subscription(int id, String name, BigDecimal cost, BillingCycle cycle,
            LocalDate nextPayment, String category) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.cycle = cycle;
        this.nextPayment = nextPayment;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public BillingCycle getCycle() {
        return cycle;
    }

    public void setCycle(BillingCycle cycle) {
        this.cycle = cycle;
    }

    public LocalDate getNextPayment() {
        return nextPayment;
    }

    public void setNextPayment(LocalDate nextPayment) {
        this.nextPayment = nextPayment;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getMonthlyCost() {
        return cycle.toMonthly(cost);
    }

    public BigDecimal getYearlyCost() {
        return cycle.toYearly(cost);
    }

    /**
     * Moves the next payment date forward past any payments that have
     * already happened, so it always points at today or later.
     *
     * @return true if the date changed
     */
    public boolean rollForward(LocalDate today) {
        boolean changed = false;
        while (nextPayment.isBefore(today)) {
            nextPayment = cycle.next(nextPayment);
            changed = true;
        }
        return changed;
    }
}
