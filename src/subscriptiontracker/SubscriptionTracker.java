package subscriptiontracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
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
        showTrialReminders(7);
        showBudgetWarning();

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
                case "10" -> monthlyBudget();
                case "11" -> listFreeTrials();
                case "12" -> cancelSubscription();
                case "13" -> cancelledSubscriptions();
                case "14" -> filterByCycle();
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
        System.out.println("10. Monthly budget");
        System.out.println("11. Free trials");
        System.out.println("12. Cancel a subscription");
        System.out.println("13. Cancelled subscriptions and savings");
        System.out.println("14. Filter by billing cycle");
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
        boolean trial = readYesNo("Free trial? (y/n)", false);
        LocalDate next = readDate(dateLabel(trial), null);
        if (next == null) {
            return;
        }
        String category = readText("Category (e.g. Streaming, Music, Software)", "Other");

        Subscription sub = manager.add(name, cost, cycle, next, category);
        sub.setFreeTrial(trial);
        sub.rollForward(LocalDate.now());
        save();
        System.out.println("Added \"" + name + "\" (#" + sub.getId() + ").");
        if (sub.isFreeTrial()) {
            boolean withinAWeek = sub.getNextPayment().isBefore(LocalDate.now().plusDays(8));
            System.out.println("Its free trial ends " + sub.getNextPayment() + " (" + dueIn(sub.getNextPayment())
                    + "). " + (withinAWeek ? "Cancel before then if you don't want to be charged."
                            : "You'll be reminded when it's a week away."));
        }
        showBudgetWarning();
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
        sub.setFreeTrial(readYesNo("Free trial? (y/n)", sub.isFreeTrial()));
        sub.setNextPayment(readDate(dateLabel(sub.isFreeTrial()), sub.getNextPayment()));
        sub.setCategory(readText("Category", sub.getCategory()));
        sub.rollForward(LocalDate.now());
        save();
        System.out.println("Updated \"" + sub.getName() + "\".");
        showBudgetWarning();
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
        if (manager.getMonthlyBudget().isPresent()) {
            System.out.println("Budget:        " + budgetUse());
        }
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
        printMatchTotals(matches);
    }

    private void filterByCycle() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet.");
            return;
        }
        System.out.println("Show subscriptions billed (blank to go back):");
        BillingCycle cycle = readCycle(null);
        if (cycle == null) {
            return;
        }
        List<Subscription> matches = manager.getByCycle(cycle);
        if (matches.isEmpty()) {
            System.out.println("No " + cycle.getLabel().toLowerCase(Locale.ROOT) + " subscriptions.");
            return;
        }
        printTable(matches);
        printMatchTotals(matches);
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
            System.out.printf(format, s.getId(), shorten(displayName(s), 20), money(s.getCost()),
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
            System.out.printf(format, s.getId(), shorten(displayName(s), 20), money(s.getCost()),
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

    private void monthlyBudget() {
        Optional<BigDecimal> current = manager.getMonthlyBudget();
        if (current.isPresent()) {
            System.out.println("Your monthly budget: " + budgetUse());
        } else {
            System.out.println("You haven't set a monthly budget.");
        }
        BigDecimal budget = readCost("New monthly budget (0 to remove, blank to keep)", current.orElse(null));
        if (budget == null || (current.isPresent() && budget.compareTo(current.get()) == 0)) {
            System.out.println("Monthly budget unchanged.");
            return;
        }
        manager.setMonthlyBudget(budget);
        save();
        if (manager.getMonthlyBudget().isEmpty()) {
            System.out.println("Monthly budget removed.");
        } else {
            System.out.println("Monthly budget set to " + money(budget) + ".");
            showBudgetWarning();
        }
    }

    private void listFreeTrials() {
        List<Subscription> trials = manager.getFreeTrials();
        if (trials.isEmpty()) {
            System.out.println("You have no free trials. Answer \"y\" to \"Free trial?\" when adding "
                    + "or editing a subscription to track one.");
            return;
        }
        String format = "%-4s %-20s %-12s %-14s %12s  %-10s%n";
        System.out.printf(format, "ID", "Name", "Trial ends", "Ends", "Then costs", "Cycle");
        System.out.println("-".repeat(78));
        for (Subscription s : trials) {
            System.out.printf(format, s.getId(), shorten(s.getName(), 20), s.getNextPayment(),
                    dueIn(s.getNextPayment()), money(s.getCost()), s.getCycle().getLabel());
        }
        System.out.println("Cancel before the end date if you don't want to be charged.");
    }

    private void cancelSubscription() {
        Optional<Subscription> found = pickSubscription("cancel");
        if (found.isEmpty()) {
            return;
        }
        Subscription sub = found.get();
        String answer = prompt("Cancel \"" + sub.getName() + "\"? It moves to your cancelled list. (y/n)");
        if (answer == null || !answer.equalsIgnoreCase("y")) {
            System.out.println("Nothing cancelled.");
            return;
        }
        sub.cancel(LocalDate.now());
        save();
        System.out.println("Cancelled \"" + sub.getName() + "\". You'll save " + money(sub.getMonthlyCost())
                + " a month (" + money(sub.getYearlyCost()) + " a year).");
    }

    private void cancelledSubscriptions() {
        List<Subscription> cancelled = manager.getCancelled();
        if (cancelled.isEmpty()) {
            System.out.println("You haven't cancelled any subscriptions. Choose 12 to cancel one.");
            return;
        }
        LocalDate today = LocalDate.now();
        String format = "%-4s %-20s %-12s %12s %14s%n";
        System.out.printf(format, "ID", "Name", "Cancelled", "Per month", "Saved so far");
        System.out.println("-".repeat(66));
        for (Subscription s : cancelled) {
            System.out.printf(format, s.getId(), shorten(s.getName(), 20), s.getCancelledOn(),
                    money(s.getMonthlyCost()), money(s.getSavedSoFar(today)));
        }
        BigDecimal yearly = cancelled.stream().map(Subscription::getYearlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("Saved so far: " + money(manager.getSavedSoFar(today)));
        System.out.println("Cancelling these saves you " + money(manager.getCancelledMonthlyTotal())
                + " a month (" + money(yearly) + " a year).");

        String input = prompt("ID to restore if you've signed up again (blank to go back)");
        if (input == null || input.isEmpty()) {
            return;
        }
        Optional<Subscription> found;
        try {
            found = manager.find(Integer.parseInt(input)).filter(Subscription::isCancelled);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a number.");
            return;
        }
        if (found.isEmpty()) {
            System.out.println("No cancelled subscription with ID " + input + ".");
            return;
        }
        Subscription sub = found.get();
        sub.reactivate(today);
        save();
        System.out.println("Restored \"" + sub.getName() + "\". Next payment: " + sub.getNextPayment()
                + " (" + dueIn(sub.getNextPayment()) + ").");
        showBudgetWarning();
    }

    // ---- Display helpers ----

    /** Reminds about free trials that end soon, before they start charging. */
    private void showTrialReminders(int days) {
        for (Subscription s : manager.getTrialsEndingWithin(LocalDate.now(), days)) {
            System.out.println("Reminder: your " + s.getName() + " free trial ends " + dueIn(s.getNextPayment())
                    + " (" + s.getNextPayment() + "). You'll be charged " + money(s.getCost())
                    + " unless you cancel.");
        }
    }

    /** The name to show in lists, marking free trials. */
    private static String displayName(Subscription s) {
        return s.isFreeTrial() ? s.getName() + " (trial)" : s.getName();
    }

    private static String dateLabel(boolean trial) {
        return trial ? "Trial end / first payment date (YYYY-MM-DD)" : "Next payment date (YYYY-MM-DD)";
    }

    /** Warns when monthly spending is over the budget or close to it. */
    private void showBudgetWarning() {
        switch (manager.getBudgetStatus()) {
            case OVER -> System.out.println("Warning: you're over your monthly budget. " + budgetUse());
            case NEAR -> System.out.println("Heads up: you're close to your monthly budget. " + budgetUse());
            default -> {
                // Under budget, or no budget set: nothing to say.
            }
        }
    }

    /**
     * Describes spending against the budget, e.g.
     * "500.00 a month; you're spending 458.99 (91%), 41.01 left."
     */
    private String budgetUse() {
        BigDecimal budget = manager.getMonthlyBudget().orElseThrow();
        BigDecimal monthly = manager.getMonthlyTotal();
        BigDecimal percent = monthly.multiply(BigDecimal.valueOf(100)).divide(budget, 0, RoundingMode.DOWN);
        BigDecimal left = budget.subtract(monthly);
        String remaining = left.signum() >= 0 ? money(left) + " left" : money(left.negate()) + " over";
        return money(budget) + " a month; you're spending " + money(monthly)
                + " (" + percent + "%), " + remaining + ".";
    }

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
                    displayName(s), money(s.getCost()), s.getNextPayment(), dueIn(s.getNextPayment()));
            total = total.add(s.getCost());
        }
        System.out.println("  Total due: " + money(total));
    }

    private void printTable(List<Subscription> subs) {
        String format = "%-4s %-20s %12s %-10s %-12s %-15s%n";
        System.out.printf(format, "ID", "Name", "Cost", "Cycle", "Next due", "Category");
        System.out.println("-".repeat(78));
        for (Subscription s : subs) {
            System.out.printf(format, s.getId(), shorten(displayName(s), 20), money(s.getCost()),
                    s.getCycle().getLabel(), s.getNextPayment(), shorten(s.getCategory(), 15));
        }
    }

    /** Prints how many subscriptions matched and what they cost together. */
    private void printMatchTotals(List<Subscription> matches) {
        BigDecimal monthly = matches.stream().map(Subscription::getMonthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal yearly = matches.stream().map(Subscription::getYearlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println(matches.size() + " found. Monthly: " + money(monthly)
                + "  Yearly: " + money(yearly));
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

    /** Reads a yes/no answer; blank keeps the current answer. */
    private boolean readYesNo(String label, boolean current) {
        while (true) {
            String input = prompt(label + " [" + (current ? "y" : "n") + "]");
            if (input == null || input.isEmpty()) {
                return current;
            }
            switch (input.toLowerCase(Locale.ROOT)) {
                case "y", "yes" -> {
                    return true;
                }
                case "n", "no" -> {
                    return false;
                }
                default -> System.out.println("Please answer y or n.");
            }
        }
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
        String input = prompt("ID of the subscription to " + action + " (blank to go back)");
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<Subscription> found = manager.find(Integer.parseInt(input))
                    .filter(s -> !s.isCancelled());
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
                keepUnreadableCopy();
            }
            if (manager.rollForwardAll(LocalDate.now()) > 0) {
                save();
            }
        } catch (IOException e) {
            System.out.println("Could not read " + storage.getFile() + ": " + e.getMessage());
        }
    }

    private void keepUnreadableCopy() {
        try {
            Path copy = storage.keepUnreadableCopy();
            System.out.println("The file as it was has been copied to " + copy + ", so those lines aren't lost.");
        } catch (IOException e) {
            System.out.println("Could not copy " + storage.getFile() + ": " + e.getMessage());
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
