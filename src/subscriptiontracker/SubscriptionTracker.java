package subscriptiontracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * Console app for keeping track of subscriptions and what they cost.
 */
public class SubscriptionTracker {

    private static final String DATA_FILE = "subscriptions.txt";
    private static final String CSV_FILE = "subscriptions.csv";

    private final SubscriptionManager manager = new SubscriptionManager();
    private final SubscriptionStorage storage;
    private final Scanner in;

    public SubscriptionTracker(SubscriptionStorage storage, Scanner in) {
        this.storage = storage;
        this.in = in;
    }

    public static void main(String[] args) {
        Path file = Path.of(args.length > 0 ? args[0] : DATA_FILE);
        new SubscriptionTracker(new SubscriptionStorage(file), new Scanner(System.in)).run();
    }

    public void run() {
        load();
        System.out.println("=== Subscription Tracker ===");
        showUpcoming(7);

        while (true) {
            printMenu();
            String choice = prompt("Choose an option");
            if (choice == null) {
                return;
            }
            switch (choice) {
                case "1" -> listAll();
                case "2" -> addSubscription();
                case "3" -> editSubscription();
                case "4" -> removeSubscription();
                case "5" -> upcomingPayments();
                case "6" -> spendingSummary();
                case "7" -> searchByCategory();
                case "8" -> exportToCsv();
                case "9" -> sortSubscriptions();
                case "0" -> {
                    System.out.println("Goodbye!");
                    return;
                }
                default -> System.out.println("Please enter a number from the menu.");
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("1. View all subscriptions");
        System.out.println("2. Add a subscription");
        System.out.println("3. Edit a subscription");
        System.out.println("4. Remove a subscription");
        System.out.println("5. Upcoming payments");
        System.out.println("6. Spending summary");
        System.out.println("7. Search by category");
        System.out.println("8. Export to CSV");
        System.out.println("9. Sort subscriptions");
        System.out.println("0. Exit");
    }

    // ---- Menu actions ----

    private void listAll() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet. Choose 2 to add one.");
            return;
        }
        printTable(manager.getAll());
    }

    private void addSubscription() {
        System.out.println("-- Add a subscription (leave blank to cancel) --");
        String name = readText("Name", null);
        if (name == null) {
            return;
        }
        BigDecimal cost = readCost("Cost per payment", null);
        if (cost == null) {
            return;
        }
        BillingCycle cycle = readCycle(null);
        if (cycle == null) {
            return;
        }
        LocalDate next = readDate("Next payment date (YYYY-MM-DD)", null);
        if (next == null) {
            return;
        }
        String category = readText("Category (e.g. Streaming, Music, Software)", "Other");

        Subscription sub = manager.add(name, cost, cycle, next, category);
        sub.rollForward(LocalDate.now());
        save();
        System.out.println("Added \"" + name + "\" (#" + sub.getId() + ").");
    }

    private void editSubscription() {
        Optional<Subscription> found = pickSubscription("edit");
        if (found.isEmpty()) {
            return;
        }
        Subscription sub = found.get();
        System.out.println("-- Press Enter to keep the current value --");
        sub.setName(readText("Name", sub.getName()));
        sub.setCost(readCost("Cost per payment", sub.getCost()));
        sub.setCycle(readCycle(sub.getCycle()));
        sub.setNextPayment(readDate("Next payment date (YYYY-MM-DD)", sub.getNextPayment()));
        sub.setCategory(readText("Category", sub.getCategory()));
        sub.rollForward(LocalDate.now());
        save();
        System.out.println("Updated \"" + sub.getName() + "\".");
    }

    private void removeSubscription() {
        Optional<Subscription> found = pickSubscription("remove");
        if (found.isEmpty()) {
            return;
        }
        Subscription sub = found.get();
        String answer = prompt("Remove \"" + sub.getName() + "\"? (y/n)");
        if (answer != null && answer.equalsIgnoreCase("y")) {
            manager.remove(sub.getId());
            save();
            System.out.println("Removed \"" + sub.getName() + "\".");
        } else {
            System.out.println("Nothing removed.");
        }
    }

    private void upcomingPayments() {
        String input = prompt("Show payments due in the next how many days? [30]");
        int days = 30;
        if (input != null && !input.isEmpty()) {
            try {
                days = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("That isn't a number, showing the next 30 days.");
            }
            if (days < 0) {
                days = 30;
            }
        }
        showUpcoming(days);
    }

    private void spendingSummary() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet.");
            return;
        }
        BigDecimal monthly = manager.getMonthlyTotal();
        System.out.println("-- Spending summary --");
        System.out.println("Monthly total: " + money(monthly));
        System.out.println("Yearly total:  " + money(manager.getYearlyTotal()));
        System.out.println();
        System.out.println("By category (per month):");
        for (Map.Entry<String, BigDecimal> e : manager.getMonthlyByCategory().entrySet()) {
            BigDecimal percent = monthly.signum() == 0 ? BigDecimal.ZERO
                    : e.getValue().multiply(BigDecimal.valueOf(100)).divide(monthly, 0, RoundingMode.HALF_UP);
            System.out.printf("  %-20s %12s  (%s%%)%n", e.getKey(), money(e.getValue()), percent);
        }
    }

    private void searchByCategory() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet.");
            return;
        }
        System.out.println("Categories: " + String.join(", ", manager.getMonthlyByCategory().keySet()));
        String query = prompt("Category to search for (blank to cancel)");
        if (query == null || query.isEmpty()) {
            return;
        }
        List<Subscription> matches = manager.searchByCategory(query);
        if (matches.isEmpty()) {
            System.out.println("No subscriptions in a category matching \"" + query + "\".");
            return;
        }
        printTable(matches);
        BigDecimal monthly = matches.stream().map(Subscription::getMonthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal yearly = matches.stream().map(Subscription::getYearlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println(matches.size() + " found. Monthly: " + money(monthly)
                + "  Yearly: " + money(yearly));
    }

    private void exportToCsv() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions to export.");
            return;
        }
        String name = readText("File to export to", CSV_FILE);
        try {
            Path file = Path.of(name);
            CsvExporter.export(manager.getAll(), file);
            System.out.println("Exported " + manager.getAll().size() + " subscription(s) to "
                    + file.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            System.out.println("Could not export to " + name + ": " + e.getMessage());
        }
    }

    private void sortSubscriptions() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet.");
            return;
        }
        System.out.println("Sort by:");
        System.out.println("  1. Cost per month");
        System.out.println("  2. Next payment date");
        System.out.println("  3. Name");
        System.out.println("  4. Category");
        System.out.println("  5. Billing cycle");
        System.out.println("  6. ID");
        String choice = prompt("Sort by (blank to cancel)");
        if (choice == null || choice.isEmpty()) {
            return;
        }
        switch (choice) {
            case "1" -> sortByCost();
            case "2" -> sortByNextPayment();
            case "3" -> sortByName();
            case "4" -> sortByCategory();
            case "5" -> sortByCycle();
            case "6" -> sortById();
            default -> System.out.println("Please choose 1-6.");
        }
    }

    private void sortByCost() {
        System.out.println("  1. Most expensive first");
        System.out.println("  2. Cheapest first");
        String choice = prompt("Order [1]");
        if (choice == null) {
            return;
        }
        boolean highestFirst = !choice.equals("2");
        List<Subscription> sorted = manager.getSortedByCost(highestFirst);

        String format = "%-4s %-20s %12s %-10s %12s  %-15s%n";
        System.out.printf(format, "ID", "Name", "Cost", "Cycle", "Per month", "Category");
        System.out.println("-".repeat(80));
        for (Subscription s : sorted) {
            System.out.printf(format, s.getId(), shorten(s.getName(), 20), money(s.getCost()),
                    s.getCycle().getLabel(), money(s.getMonthlyCost()), shorten(s.getCategory(), 15));
        }
        System.out.println("Sorted by cost per month, " + (highestFirst ? "most expensive" : "cheapest") + " first.");
    }

    private void sortByNextPayment() {
        System.out.println("  1. Soonest first");
        System.out.println("  2. Latest first");
        String choice = prompt("Order [1]");
        if (choice == null) {
            return;
        }
        boolean soonestFirst = !choice.equals("2");
        List<Subscription> sorted = manager.getSortedByNextPayment(soonestFirst);

        String format = "%-4s %-20s %12s %-10s %-12s %-14s%n";
        System.out.printf(format, "ID", "Name", "Cost", "Cycle", "Next due", "Due in");
        System.out.println("-".repeat(78));
        for (Subscription s : sorted) {
            System.out.printf(format, s.getId(), shorten(s.getName(), 20), money(s.getCost()),
                    s.getCycle().getLabel(), s.getNextPayment(), dueIn(s.getNextPayment()));
        }
        System.out.println("Sorted by next payment date, " + (soonestFirst ? "soonest" : "latest") + " first.");
    }

    private void sortByName() {
        System.out.println("  1. A to Z");
        System.out.println("  2. Z to A");
        String choice = prompt("Order [1]");
        if (choice == null) {
            return;
        }
        boolean aToZ = !choice.equals("2");
        printTable(manager.getSortedByName(aToZ));
        System.out.println("Sorted by name, " + (aToZ ? "A to Z" : "Z to A") + ".");
    }

    private void sortByCategory() {
        System.out.println("  1. A to Z");
        System.out.println("  2. Z to A");
        String choice = prompt("Order [1]");
        if (choice == null) {
            return;
        }
        boolean aToZ = !choice.equals("2");
        printTable(manager.getSortedByCategory(aToZ));
        System.out.println("Sorted by category, " + (aToZ ? "A to Z" : "Z to A") + ".");
    }

    private void sortByCycle() {
        System.out.println("  1. Shortest first (Weekly to Yearly)");
        System.out.println("  2. Longest first (Yearly to Weekly)");
        String choice = prompt("Order [1]");
        if (choice == null) {
            return;
        }
        boolean shortestFirst = !choice.equals("2");
        printTable(manager.getSortedByCycle(shortestFirst));
        System.out.println("Sorted by billing cycle, " + (shortestFirst ? "shortest" : "longest") + " first.");
    }

    private void sortById() {
        System.out.println("  1. Lowest first (oldest added first)");
        System.out.println("  2. Highest first (newest added first)");
        String choice = prompt("Order [1]");
        if (choice == null) {
            return;
        }
        boolean lowestFirst = !choice.equals("2");
        printTable(manager.getSortedById(lowestFirst));
        System.out.println("Sorted by ID, " + (lowestFirst ? "lowest" : "highest") + " first.");
    }

    // ---- Display helpers ----

    private void showUpcoming(int days) {
        List<Subscription> due = manager.getUpcoming(LocalDate.now(), days);
        if (due.isEmpty()) {
            if (!manager.isEmpty()) {
                System.out.println("No payments due in the next " + days + " days.");
            }
            return;
        }
        System.out.println("Payments due in the next " + days + " days:");
        BigDecimal total = BigDecimal.ZERO;
        for (Subscription s : due) {
            System.out.printf("  %-20s %12s  %s (%s)%n",
                    s.getName(), money(s.getCost()), s.getNextPayment(), dueIn(s.getNextPayment()));
            total = total.add(s.getCost());
        }
        System.out.println("  Total due: " + money(total));
    }

    private void printTable(List<Subscription> subs) {
        String format = "%-4s %-20s %12s %-10s %-12s %-15s%n";
        System.out.printf(format, "ID", "Name", "Cost", "Cycle", "Next due", "Category");
        System.out.println("-".repeat(78));
        for (Subscription s : subs) {
            System.out.printf(format, s.getId(), shorten(s.getName(), 20), money(s.getCost()),
                    s.getCycle().getLabel(), s.getNextPayment(), shorten(s.getCategory(), 15));
        }
    }

    /** Describes how far away a date is, e.g. "today", "tomorrow" or "in 5 days". */
    private static String dueIn(LocalDate date) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
        return days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
    }

    private static String money(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    // ---- Input helpers ----

    /** Prints a prompt and reads a trimmed line, or null if input has ended. */
    private String prompt(String message) {
        System.out.print(message + ": ");
        if (!in.hasNextLine()) {
            System.out.println();
            return null;
        }
        return in.nextLine().trim();
    }

    private static String withDefault(String label, Object current) {
        return current == null ? label : label + " [" + current + "]";
    }

    /** Reads text; blank input returns the current value (null means cancel). */
    private String readText(String label, String current) {
        String input = prompt(withDefault(label, current));
        if (input == null || input.isEmpty()) {
            return current;
        }
        return input.replace("\t", " ");
    }

    private BigDecimal readCost(String label, BigDecimal current) {
        while (true) {
            String input = prompt(withDefault(label, current == null ? null : current.toPlainString()));
            if (input == null || input.isEmpty()) {
                return current;
            }
            try {
                BigDecimal cost = new BigDecimal(input.replace(",", ""));
                if (cost.signum() >= 0) {
                    return cost.setScale(2, RoundingMode.HALF_UP);
                }
            } catch (NumberFormatException e) {
                // fall through to the message below
            }
            System.out.println("Please enter a positive amount, e.g. 99.99");
        }
    }

    private BillingCycle readCycle(BillingCycle current) {
        BillingCycle[] cycles = BillingCycle.values();
        for (int i = 0; i < cycles.length; i++) {
            System.out.println("  " + (i + 1) + ". " + cycles[i].getLabel());
        }
        while (true) {
            String input = prompt(withDefault("Billing cycle", current == null ? null : current.getLabel()));
            if (input == null || input.isEmpty()) {
                return current;
            }
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= cycles.length) {
                    return cycles[choice - 1];
                }
            } catch (NumberFormatException e) {
                // fall through to the message below
            }
            System.out.println("Please choose 1-" + cycles.length + ".");
        }
    }

    private LocalDate readDate(String label, LocalDate current) {
        while (true) {
            String input = prompt(withDefault(label, current));
            if (input == null || input.isEmpty()) {
                return current;
            }
            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException e) {
                System.out.println("Please use the format YYYY-MM-DD, e.g. " + LocalDate.now());
            }
        }
    }

    private Optional<Subscription> pickSubscription(String action) {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet.");
            return Optional.empty();
        }
        printTable(manager.getAll());
        String input = prompt("ID of the subscription to " + action + " (blank to cancel)");
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<Subscription> found = manager.find(Integer.parseInt(input));
            if (found.isEmpty()) {
                System.out.println("No subscription with ID " + input + ".");
            }
            return found;
        } catch (NumberFormatException e) {
            System.out.println("Please enter a number.");
            return Optional.empty();
        }
    }

    // ---- Persistence ----

    private void load() {
        try {
            int skipped = storage.load(manager);
            if (skipped > 0) {
                System.out.println("Warning: skipped " + skipped + " unreadable line(s) in " + storage.getFile());
            }
            if (manager.rollForwardAll(LocalDate.now()) > 0) {
                save();
            }
        } catch (IOException e) {
            System.out.println("Could not read " + storage.getFile() + ": " + e.getMessage());
        }
    }

    private void save() {
        try {
            storage.save(manager);
        } catch (IOException e) {
            System.out.println("Could not save to " + storage.getFile() + ": " + e.getMessage());
        }
    }
}
