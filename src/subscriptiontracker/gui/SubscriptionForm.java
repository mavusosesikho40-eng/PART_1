package subscriptiontracker.gui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.CsvImporter;
import subscriptiontracker.Display;
import subscriptiontracker.PriceChange;
import subscriptiontracker.Subscription;
import subscriptiontracker.SubscriptionManager;

/**
 * What's typed into the add/edit dialog: checked and applied here, with no
 * window involved, so it can be tested on its own.
 */
public class SubscriptionForm {

    private String name = "";
    private String cost = "";
    private BillingCycle cycle = BillingCycle.MONTHLY;
    private boolean freeTrial;
    private String date = "";
    private String category = "";
    private String note = "";
    private boolean recordPriceChange = true;

    /** A form filled in with a subscription's current values, for editing it. */
    public static SubscriptionForm from(Subscription s) {
        SubscriptionForm form = new SubscriptionForm();
        form.name = s.getName();
        form.cost = s.getCost().toPlainString();
        form.cycle = s.getCycle();
        form.freeTrial = s.isFreeTrial();
        form.date = s.getNextPayment().toString();
        form.category = s.getCategory();
        form.note = s.getNote();
        return form;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCost() {
        return cost;
    }

    public void setCost(String cost) {
        this.cost = cost;
    }

    public BillingCycle getCycle() {
        return cycle;
    }

    public void setCycle(BillingCycle cycle) {
        this.cycle = cycle;
    }

    public boolean isFreeTrial() {
        return freeTrial;
    }

    public void setFreeTrial(boolean freeTrial) {
        this.freeTrial = freeTrial;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isRecordPriceChange() {
        return recordPriceChange;
    }

    /** Whether a changed cost is recorded as a price change (true) or fixes a mistake (false). */
    public void setRecordPriceChange(boolean recordPriceChange) {
        this.recordPriceChange = recordPriceChange;
    }

    /** The cost as an amount, or null if it isn't one (or is negative). */
    public BigDecimal parsedCost() {
        try {
            return cost.isBlank() ? null : CsvImporter.parseAmount(cost);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The date, written as 2026-10-01, 2026/10/01 or 01/10/2026, or null if it isn't one. */
    public LocalDate parsedDate() {
        try {
            return CsvImporter.parseDate(date.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** What stops the form being saved, as a sentence, or null if it can be saved. */
    public String problem() {
        if (oneLine(name).isEmpty()) {
            return "Please enter a name.";
        }
        if (parsedCost() == null) {
            return "Please enter the cost as an amount, e.g. 99.99.";
        }
        if (parsedDate() == null) {
            return "Please enter the date like 2026-10-01 or 01/10/2026.";
        }
        return null;
    }

    /**
     * The price change saving would record for this subscription, or null if
     * the cost is unchanged (or not yet a valid amount).
     */
    public PriceChange pendingPriceChange(Subscription s, LocalDate today) {
        BigDecimal newCost = parsedCost();
        if (newCost == null || newCost.compareTo(s.getCost()) == 0) {
            return null;
        }
        return new PriceChange(today, s.getCost(), newCost, s.getCycle(), cycle);
    }

    /**
     * Describes when payments fall, e.g. "Billed on the 1st of each month · in 5 days",
     * or null if the date isn't valid yet. A date in the past says where it will move to.
     */
    public String billingDescription(LocalDate today) {
        LocalDate first = parsedDate();
        if (first == null) {
            return null;
        }
        String when = switch (cycle) {
            case WEEKLY -> "Billed every " + first.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            case MONTHLY -> "Billed on the " + ordinal(first.getDayOfMonth()) + " of each month";
            case QUARTERLY -> "Billed every 3 months on the " + ordinal(first.getDayOfMonth());
            case YEARLY -> "Billed every year on " + first.getDayOfMonth() + " "
                    + first.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        };
        Subscription preview = new Subscription(0, "", BigDecimal.ONE, cycle, first, "");
        preview.rollForward(today);
        LocalDate next = preview.getNextPayment();
        if (next.equals(first)) {
            return when + " · " + Display.dueIn(first);
        }
        return when + " · that date has passed, so the next one is " + Theme.DATE.format(next);
    }

    /** Adds a new subscription from the form. Call only when {@link #problem()} is null. */
    public Subscription add(SubscriptionManager manager, LocalDate today) {
        String categoryText = oneLine(category);
        Subscription sub = manager.add(oneLine(name), parsedCost(), cycle, parsedDate(),
                categoryText.isEmpty() ? "Other" : categoryText);
        sub.setFreeTrial(freeTrial);
        sub.setNote(oneLine(note));
        sub.rollForward(today);
        return sub;
    }

    /**
     * Saves the form into an existing subscription. Call only when
     * {@link #problem()} is null.
     *
     * @return the price change that was recorded, or null if none was
     */
    public PriceChange applyTo(Subscription sub, LocalDate today) {
        BigDecimal newCost = parsedCost();
        PriceChange recorded = null;
        if (newCost.compareTo(sub.getCost()) != 0 && recordPriceChange) {
            sub.changePrice(newCost, cycle, today);
            recorded = sub.getPriceChanges().get(sub.getPriceChanges().size() - 1);
        } else {
            sub.setCost(newCost);
            sub.setCycle(cycle);
        }
        sub.setName(oneLine(name));
        sub.setFreeTrial(freeTrial);
        sub.setNextPayment(parsedDate());
        String categoryText = oneLine(category);
        sub.setCategory(categoryText.isEmpty() ? "Other" : categoryText);
        sub.setNote(oneLine(note));
        sub.rollForward(today);
        return recorded;
    }

    /**
     * Text on one line: line breaks and tabs become spaces, which the data
     * file needs (each subscription is one tab-separated line).
     */
    static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("[\\t\\r\\n]+", " ").strip();
    }

    /** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st, 22nd, 23rd … 31st. */
    static String ordinal(int day) {
        if (day >= 11 && day <= 13) {
            return day + "th";
        }
        return day + switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
