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
    private int billingDay;
    private String category;
    private boolean freeTrial;
    private LocalDate cancelledOn;
    private String note = "";
    private final List<PriceChange> priceChanges = new ArrayList<>();

    public Subscription(int id, String name, BigDecimal cost, BillingCycle cycle,
            LocalDate nextPayment, String category) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.cycle = cycle;
        this.nextPayment = nextPayment;
        this.billingDay = nextPayment.getDayOfMonth();
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

    /**
     * Sets the next payment date. A different date also becomes the day of
     * the month the subscription is billed on; setting the same date again
     * keeps the billing day (so a payment moved to 28 Feb stays billed on the 31st).
     */
    public void setNextPayment(LocalDate nextPayment) {
        if (!nextPayment.equals(this.nextPayment)) {
            this.billingDay = nextPayment.getDayOfMonth();
        }
        this.nextPayment = nextPayment;
    }

    /**
     * The day of the month payments are meant to fall on. It can be later
     * than the next payment's day when that month is too short.
     */
    public int getBillingDay() {
        return billingDay;
    }

    /** Sets the billing day loaded from storage (1 to 31). */
    public void restoreBillingDay(int day) {
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("billing day " + day);
        }
        billingDay = day;
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

    /** A free-text note, e.g. which account or card it's on; empty if there is none. */
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note == null ? "" : note.strip();
    }

    public boolean hasNote() {
        return !note.isEmpty();
    }

    /**
     * Changes the cost per payment and records the old and new price, so
     * price rises can be tracked. Nothing is recorded if the cost is the same.
     */
    public void changePrice(BigDecimal newCost, LocalDate today) {
        changePrice(newCost, cycle, today);
    }

    /**
     * Changes the cost and billing cycle together (e.g. switching to a yearly
     * plan at a new price) and records the change. Nothing is recorded if
     * neither changed.
     */
    public void changePrice(BigDecimal newCost, BillingCycle newCycle, LocalDate today) {
        if (newCost.compareTo(cost) == 0 && newCycle == cycle) {
            return;
        }
        priceChanges.add(new PriceChange(today, cost, newCost, cycle, newCycle));
        cost = newCost;
        cycle = newCycle;
    }

    /** Adds a price change loaded from storage; the current cost is left as it is. */
    public void restorePriceChange(PriceChange change) {
        priceChanges.add(change);
    }

    /** Recorded price changes, oldest first. */
    public List<PriceChange> getPriceChanges() {
        return Collections.unmodifiableList(priceChanges);
    }

    /** What it cost per month on the given day, based on the recorded price changes. */
    public BigDecimal getMonthlyCostOn(LocalDate date) {
        for (PriceChange change : priceChanges) {
            if (change.date().isAfter(date)) {
                return change.oldCycle().toMonthly(change.oldCost());
            }
        }
        return getMonthlyCost();
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
        for (LocalDate d = nextPayment; !d.isAfter(today); d = cycle.next(d, billingDay)) {
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
            nextPayment = cycle.next(nextPayment, billingDay);
            changed = true;
        }
        if (changed) {
            freeTrial = false;
        }
        return changed;
    }
}
