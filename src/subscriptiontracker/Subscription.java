package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private LocalDate cancelledOn;
    private final List<PriceChange> priceChanges = new ArrayList<>();

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

    /**
     * Changes the cost per payment and records the old and new price, so
     * price rises can be tracked. Nothing is recorded if the cost is the same.
     */
    public void changePrice(BigDecimal newCost, LocalDate today) {
        if (newCost.compareTo(cost) == 0) {
            return;
        }
        priceChanges.add(new PriceChange(today, cost, newCost));
        cost = newCost;
    }

    /** Adds a price change loaded from storage; the current cost is left as it is. */
    public void restorePriceChange(PriceChange change) {
        priceChanges.add(change);
    }

    /** Recorded price changes, oldest first. */
    public List<PriceChange> getPriceChanges() {
        return Collections.unmodifiableList(priceChanges);
    }

    /** What each payment cost on the given day, based on the recorded price changes. */
    public BigDecimal getCostOn(LocalDate date) {
        for (PriceChange change : priceChanges) {
            if (change.date().isAfter(date)) {
                return change.oldCost();
            }
        }
        return cost;
    }

    public boolean isCancelled() {
        return cancelledOn != null;
    }

    /** The day it was cancelled, or null if it is active. */
    public LocalDate getCancelledOn() {
        return cancelledOn;
    }

    /**
     * Cancels it. The next payment date is kept as the first payment that
     * won't be made, which is where savings are counted from.
     */
    public void cancel(LocalDate today) {
        cancelledOn = today;
    }

    /** Makes a cancelled subscription active again from today. */
    public void reactivate(LocalDate today) {
        cancelledOn = null;
        rollForward(today);
    }

    /**
     * How much has been saved by cancelling: the payments that would have
     * been made from the next payment date up to and including today.
     */
    public BigDecimal getSavedSoFar(LocalDate today) {
        if (!isCancelled()) {
            return BigDecimal.ZERO;
        }
        int payments = 0;
        for (LocalDate d = nextPayment; !d.isAfter(today); d = cycle.next(d)) {
            payments++;
        }
        return cost.multiply(BigDecimal.valueOf(payments));
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
     * Cancelled subscriptions are left alone.
     *
     * @return true if the date changed
     */
    public boolean rollForward(LocalDate today) {
        if (isCancelled()) {
            return false;
        }
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
