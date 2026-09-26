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
    private boolean freeTrial;

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

    /**
     * Whether this is a free trial. For a trial, the next payment date is
     * the day the trial ends and the first charge is taken.
     */
    public boolean isFreeTrial() {
        return freeTrial;
    }

    public void setFreeTrial(boolean freeTrial) {
        this.freeTrial = freeTrial;
    }

    public BigDecimal getMonthlyCost() {
        return cycle.toMonthly(cost);
    }

    public BigDecimal getYearlyCost() {
        return cycle.toYearly(cost);
    }

    /**
     * Moves the next payment date forward past any payments that have
     * already happened, so it always points at today or later. A free trial
     * whose end date has passed becomes a normal paid subscription.
     *
     * @return true if the date changed
     */
    public boolean rollForward(LocalDate today) {
        boolean changed = false;
        while (nextPayment.isBefore(today)) {
            nextPayment = cycle.next(nextPayment);
            changed = true;
        }
        if (changed) {
            freeTrial = false;
        }
        return changed;
    }
}
