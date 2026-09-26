package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Formatting shared by the menu screens: amounts, dates, names and the
 * standard subscription table.
 */
final class Display {

    private static String currency = "";

    private Display() {
    }

    /**
     * Sets the currency symbol every amount is shown with; empty for none.
     * The app sets this from the data file when it starts.
     */
    static void setCurrency(String symbol) {
        currency = symbol == null ? "" : symbol;
    }

    /**
     * Formats an amount with the currency symbol, e.g. "R 1,234.50",
     * "$1,234.50" or "-R 30.00". Symbols ending in a letter get a space.
     */
    static String money(BigDecimal amount) {
        if (currency.isEmpty()) {
            return String.format("%,.2f", amount);
        }
        String gap = Character.isLetter(currency.charAt(currency.length() - 1)) ? " " : "";
        return (amount.signum() < 0 ? "-" : "") + currency + gap + String.format("%,.2f", amount.abs());
    }

    static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** Describes how far away a date is, e.g. "today", "tomorrow" or "in 5 days". */
    static String dueIn(LocalDate date) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
        return days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
    }

    /** The name to show in lists, marking free trials. */
    static String displayName(Subscription s) {
        return s.isFreeTrial() ? s.getName() + " (trial)" : s.getName();
    }

    /** An amount with a "+" in front when it's positive, e.g. "+30.00" or "-5.00". */
    static String signedMoney(BigDecimal amount) {
        return (amount.signum() > 0 ? "+" : "") + money(amount);
    }

    /** Describes a price change, e.g. "169.00 -> 199.00 (+30.00, +18%)". */
    static String describe(PriceChange change) {
        BigDecimal percent = change.percent();
        String percentText = percent == null ? "" : ", " + (percent.signum() > 0 ? "+" : "") + percent + "%";
        return money(change.oldCost()) + " -> " + money(change.newCost())
                + " (" + signedMoney(change.difference()) + percentText + ")";
    }

    static void printTable(List<Subscription> subs) {
        String format = "%-4s %-20s %12s %-10s %-12s %-15s%n";
        System.out.printf(format, "ID", "Name", "Cost", "Cycle", "Next due", "Category");
        System.out.println("-".repeat(78));
        for (Subscription s : subs) {
            System.out.printf(format, s.getId(), shorten(displayName(s), 20), money(s.getCost()),
                    s.getCycle().getLabel(), s.getNextPayment(), shorten(s.getCategory(), 15));
        }
    }

    /** Prints how many subscriptions matched and what they cost together. */
    static void printMatchTotals(List<Subscription> matches) {
        BigDecimal monthly = matches.stream().map(Subscription::getMonthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal yearly = matches.stream().map(Subscription::getYearlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println(matches.size() + " found. Monthly: " + money(monthly)
                + "  Yearly: " + money(yearly));
    }
}
