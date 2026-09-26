package subscriptiontracker;

import static subscriptiontracker.Display.dueIn;
import static subscriptiontracker.Display.money;
import static subscriptiontracker.Display.shorten;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "Spending, budget and savings" menu: the spending summary, the
 * monthly budget, and cancelled subscriptions with what they've saved.
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
                "Cancelled subscriptions and savings")) {
            case 1 -> spendingSummary();
            case 2 -> monthlyBudget();
            case 3 -> cancelledSubscriptions();
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

        String input = console.prompt("ID to restore if you've signed up again (blank to go back)");
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
        save.run();
        System.out.println("Restored \"" + sub.getName() + "\". Next payment: " + sub.getNextPayment()
                + " (" + dueIn(sub.getNextPayment()) + ").");
        showBudgetWarning();
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
