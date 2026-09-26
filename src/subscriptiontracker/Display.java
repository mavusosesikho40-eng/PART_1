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

    private Display() {
    }

    static String money(BigDecimal amount) {
        return String.format("%,.2f", amount);
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
