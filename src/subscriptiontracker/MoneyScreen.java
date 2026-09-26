package subscriptiontracker;

import static subscriptiontracker.Display.dueIn;
import static subscriptiontracker.Display.money;
import static subscriptiontracker.Display.shorten;
import static subscriptiontracker.Display.signedMoney;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "Spending, budget and savings" menu: the spending summary, the
 * monthly budget, cancelled subscriptions with what they've saved, price
 * changes, and the currency symbol amounts are shown with.
 */
class MoneyScreen {

    private final SubscriptionManager manager;
    private final Console console;
    private final Runnable save;

    MoneyScreen(SubscriptionManager manager, Console console, Runnable save) {
        this.manager = manager;
        this.console = console;
        this.save = save;
    }

    void menu() {
        switch (console.choose("Spending, budget and savings",
                "Spending summary",
                "Monthly budget",
                "Cancelled subscriptions and savings",
                "Price changes",
                "Currency symbol")) {
            case 1 -> spendingSummary();
            case 2 -> monthlyBudget();
            case 3 -> cancelledSubscriptions();
            case 4 -> priceChanges();
            case 5 -> currencySymbol();
            default -> {
                // Back to the main menu.
            }
        }
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

    private void monthlyBudget() {
        Optional<BigDecimal> current = manager.getMonthlyBudget();
        if (current.isPresent()) {
            System.out.println("Your monthly budget: " + budgetUse());
        } else {
            System.out.println("You haven't set a monthly budget.");
        }
        BigDecimal budget = console.readCost("New monthly budget (0 to remove, blank to keep)", current.orElse(null));
        if (budget == null || (current.isPresent() && budget.compareTo(current.get()) == 0)) {
            System.out.println("Monthly budget unchanged.");
            return;
        }
        manager.setMonthlyBudget(budget);
        save.run();
        if (manager.getMonthlyBudget().isEmpty()) {
            System.out.println("Monthly budget removed.");
        } else {
            System.out.println("Monthly budget set to " + money(budget) + ".");
            showBudgetWarning();
        }
    }

    private void cancelledSubscriptions() {
        List<Subscription> cancelled = manager.getCancelled();
        if (cancelled.isEmpty()) {
            System.out.println("You haven't cancelled any subscriptions. To cancel one, choose "
                    + "\"Edit, cancel or remove a subscription\" on the main menu.");
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

        String input = console.prompt("ID of one to restore or remove (blank to go back)");
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
        switch (console.choose("\"" + sub.getName() + "\"",
                "Restore it (you've signed up again)",
                "Remove it permanently")) {
            case 1 -> {
                sub.reactivate(today);
                save.run();
                System.out.println("Restored \"" + sub.getName() + "\". Next payment: " + sub.getNextPayment()
                        + " (" + dueIn(sub.getNextPayment()) + ").");
                showBudgetWarning();
            }
            case 2 -> {
                String answer = console.prompt("Remove \"" + sub.getName()
                        + "\" permanently? Its savings will no longer be counted. (y/n)");
                if (answer != null && answer.equalsIgnoreCase("y")) {
                    manager.remove(sub.getId());
                    save.run();
                    System.out.println("Removed \"" + sub.getName() + "\".");
                } else {
                    System.out.println("Nothing removed.");
                }
            }
            default -> {
                // Back to the main menu.
            }
        }
    }

    private void priceChanges() {
        List<SubscriptionManager.PriceChangeEntry> changes = manager.getPriceChanges();
        if (changes.isEmpty()) {
            System.out.println("No price changes recorded yet. When a price changes, edit the subscription's "
                    + "cost and the change is recorded here.");
            return;
        }
        String format = "%-12s %-20s %s%n";
        System.out.printf(format, "Date", "Name", "Change per payment");
        System.out.println("-".repeat(70));
        for (SubscriptionManager.PriceChangeEntry e : changes) {
            System.out.printf(format, e.change().date(), shorten(e.subscription().getName(), 20),
                    Display.describe(e.change()));
        }
        BigDecimal monthly = manager.getMonthlyPriceChangeSince(LocalDate.now().minusYears(1));
        System.out.println("Price changes in the last 12 months: " + signedMoney(monthly) + " a month ("
                + signedMoney(monthly.multiply(BigDecimal.valueOf(12))) + " a year).");
    }

    private static final BigDecimal EXAMPLE_AMOUNT = new BigDecimal("1234.50");

    private void currencySymbol() {
        if (manager.getCurrencySymbol().isEmpty()) {
            System.out.println("Amounts are shown without a currency symbol, like " + money(EXAMPLE_AMOUNT) + ".");
        } else {
            System.out.println("Amounts are shown like " + money(EXAMPLE_AMOUNT) + ".");
        }
        while (true) {
            String input = console.prompt("Currency symbol, e.g. R or $ (blank to keep, - to remove)");
            if (input == null || input.isEmpty()) {
                System.out.println("Currency symbol unchanged.");
                return;
            }
            if (input.equals("-")) {
                setCurrency("");
                System.out.println("Currency symbol removed. Amounts will look like " + money(EXAMPLE_AMOUNT) + ".");
                return;
            }
            if (Display.isValidCurrencySymbol(input)) {
                setCurrency(input);
                System.out.println("Amounts will now look like " + money(EXAMPLE_AMOUNT) + ".");
                return;
            }
            System.out.println("Please use up to 5 letters or symbols, e.g. R or $.");
        }
    }

    private void setCurrency(String symbol) {
        manager.setCurrencySymbol(symbol);
        Display.setCurrency(symbol);
        save.run();
    }

    /** Warns when monthly spending is over the budget or close to it. */
    void showBudgetWarning() {
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
}
