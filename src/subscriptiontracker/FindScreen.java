package subscriptiontracker;

import static subscriptiontracker.Display.displayName;
import static subscriptiontracker.Display.dueIn;
import static subscriptiontracker.Display.money;
import static subscriptiontracker.Display.printMatchTotals;
import static subscriptiontracker.Display.printTable;
import static subscriptiontracker.Display.shorten;

import java.util.List;
import java.util.Locale;

/**
 * The "Search, filter and sort" menu.
 */
class FindScreen {

    private final SubscriptionManager manager;
    private final Console console;

    FindScreen(SubscriptionManager manager, Console console) {
        this.manager = manager;
        this.console = console;
    }

    void menu() {
        switch (console.choose("Search, filter and sort",
                "Search by category",
                "Filter by billing cycle",
                "Sort subscriptions")) {
            case 1 -> searchByCategory();
            case 2 -> filterByCycle();
            case 3 -> sortSubscriptions();
            default -> {
                // Back to the main menu.
            }
        }
    }

    private void searchByCategory() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet.");
            return;
        }
        System.out.println("Categories: " + String.join(", ", manager.getMonthlyByCategory().keySet()));
        String query = console.prompt("Category to search for (blank to cancel)");
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
        BillingCycle cycle = console.readCycle(null);
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

    private void sortSubscriptions() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet.");
            return;
        }
        switch (console.choose("Sort by",
                "Cost per month",
                "Next payment date",
                "Name",
                "Category",
                "Billing cycle",
                "ID")) {
            case 1 -> sortByCost();
            case 2 -> sortByNextPayment();
            case 3 -> sortByName();
            case 4 -> sortByCategory();
            case 5 -> sortByCycle();
            case 6 -> sortById();
            default -> {
                // Back to the main menu.
            }
        }
    }

    /**
     * Asks which way round to sort.
     *
     * @return true for the first order, false for the second, or null if input has ended
     */
    private Boolean chooseOrder(String first, String second) {
        System.out.println("  1. " + first);
        System.out.println("  2. " + second);
        String choice = console.prompt("Order [1]");
        return choice == null ? null : !choice.equals("2");
    }

    private void sortByCost() {
        Boolean highestFirst = chooseOrder("Most expensive first", "Cheapest first");
        if (highestFirst == null) {
            return;
        }
        String format = "%-4s %-20s %12s %-10s %12s  %-15s%n";
        System.out.printf(format, "ID", "Name", "Cost", "Cycle", "Per month", "Category");
        System.out.println("-".repeat(80));
        for (Subscription s : manager.getSortedByCost(highestFirst)) {
            System.out.printf(format, s.getId(), shorten(displayName(s), 20), money(s.getCost()),
                    s.getCycle().getLabel(), money(s.getMonthlyCost()), shorten(s.getCategory(), 15));
        }
        System.out.println("Sorted by cost per month, " + (highestFirst ? "most expensive" : "cheapest") + " first.");
    }

    private void sortByNextPayment() {
        Boolean soonestFirst = chooseOrder("Soonest first", "Latest first");
        if (soonestFirst == null) {
            return;
        }
        String format = "%-4s %-20s %12s %-10s %-12s %-14s%n";
        System.out.printf(format, "ID", "Name", "Cost", "Cycle", "Next due", "Due in");
        System.out.println("-".repeat(78));
        for (Subscription s : manager.getSortedByNextPayment(soonestFirst)) {
            System.out.printf(format, s.getId(), shorten(displayName(s), 20), money(s.getCost()),
                    s.getCycle().getLabel(), s.getNextPayment(), dueIn(s.getNextPayment()));
        }
        System.out.println("Sorted by next payment date, " + (soonestFirst ? "soonest" : "latest") + " first.");
    }

    private void sortByName() {
        Boolean aToZ = chooseOrder("A to Z", "Z to A");
        if (aToZ == null) {
            return;
        }
        printTable(manager.getSortedByName(aToZ));
        System.out.println("Sorted by name, " + (aToZ ? "A to Z" : "Z to A") + ".");
    }

    private void sortByCategory() {
        Boolean aToZ = chooseOrder("A to Z", "Z to A");
        if (aToZ == null) {
            return;
        }
        printTable(manager.getSortedByCategory(aToZ));
        System.out.println("Sorted by category, " + (aToZ ? "A to Z" : "Z to A") + ".");
    }

    private void sortByCycle() {
        Boolean shortestFirst = chooseOrder("Shortest first (Weekly to Yearly)", "Longest first (Yearly to Weekly)");
        if (shortestFirst == null) {
            return;
        }
        printTable(manager.getSortedByCycle(shortestFirst));
        System.out.println("Sorted by billing cycle, " + (shortestFirst ? "shortest" : "longest") + " first.");
    }

    private void sortById() {
        Boolean lowestFirst = chooseOrder("Lowest first (oldest added first)", "Highest first (newest added first)");
        if (lowestFirst == null) {
            return;
        }
        printTable(manager.getSortedById(lowestFirst));
        System.out.println("Sorted by ID, " + (lowestFirst ? "lowest" : "highest") + " first.");
    }
}
