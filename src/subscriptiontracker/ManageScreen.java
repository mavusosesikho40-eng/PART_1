package subscriptiontracker;

import static subscriptiontracker.Display.dueIn;
import static subscriptiontracker.Display.money;
import static subscriptiontracker.Display.printTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Viewing, adding, editing, cancelling and removing subscriptions.
 */
class ManageScreen {

    private final SubscriptionManager manager;
    private final Console console;
    private final Runnable save;
    private final MoneyScreen money;

    ManageScreen(SubscriptionManager manager, Console console, Runnable save, MoneyScreen money) {
        this.manager = manager;
        this.console = console;
        this.save = save;
        this.money = money;
    }

    void listAll() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet. Choose 2 to add one.");
            return;
        }
        printTable(manager.getAll());
    }

    void addSubscription() {
        System.out.println("-- Add a subscription (leave blank to cancel) --");
        String name = console.readText("Name", null);
        if (name == null) {
            return;
        }
        BigDecimal cost = console.readCost("Cost per payment", null);
        if (cost == null) {
            return;
        }
        BillingCycle cycle = console.readCycle(null);
        if (cycle == null) {
            return;
        }
        boolean trial = console.readYesNo("Free trial? (y/n)", false);
        LocalDate next = console.readDate(dateLabel(trial), null);
        if (next == null) {
            return;
        }
        String category = console.readText("Category (e.g. Streaming, Music, Software)", "Other");

        Subscription sub = manager.add(name, cost, cycle, next, category);
        sub.setFreeTrial(trial);
        sub.rollForward(LocalDate.now());
        save.run();
        System.out.println("Added \"" + name + "\" (#" + sub.getId() + ").");
        if (sub.isFreeTrial()) {
            boolean withinAWeek = sub.getNextPayment().isBefore(LocalDate.now().plusDays(8));
            System.out.println("Its free trial ends " + sub.getNextPayment() + " (" + dueIn(sub.getNextPayment())
                    + "). " + (withinAWeek ? "Cancel before then if you don't want to be charged."
                            : "You'll be reminded when it's a week away."));
        }
        money.showBudgetWarning();
    }

    /** The "Edit, cancel or remove a subscription" menu. */
    void changeMenu() {
        switch (console.choose("Edit, cancel or remove a subscription",
                "Edit a subscription",
                "Cancel a subscription (keeps it in your cancelled list)",
                "Remove a subscription permanently")) {
            case 1 -> editSubscription();
            case 2 -> cancelSubscription();
            case 3 -> removeSubscription();
            default -> {
                // Back to the main menu.
            }
        }
    }

    private void editSubscription() {
        Optional<Subscription> found = pickSubscription("edit");
        if (found.isEmpty()) {
            return;
        }
        Subscription sub = found.get();
        System.out.println("-- Press Enter to keep the current value --");
        sub.setName(console.readText("Name", sub.getName()));
        sub.setCost(console.readCost("Cost per payment", sub.getCost()));
        sub.setCycle(console.readCycle(sub.getCycle()));
        sub.setFreeTrial(console.readYesNo("Free trial? (y/n)", sub.isFreeTrial()));
        sub.setNextPayment(console.readDate(dateLabel(sub.isFreeTrial()), sub.getNextPayment()));
        sub.setCategory(console.readText("Category", sub.getCategory()));
        sub.rollForward(LocalDate.now());
        save.run();
        System.out.println("Updated \"" + sub.getName() + "\".");
        money.showBudgetWarning();
    }

    private void cancelSubscription() {
        Optional<Subscription> found = pickSubscription("cancel");
        if (found.isEmpty()) {
            return;
        }
        Subscription sub = found.get();
        String answer = console.prompt("Cancel \"" + sub.getName() + "\"? It moves to your cancelled list. (y/n)");
        if (answer == null || !answer.equalsIgnoreCase("y")) {
            System.out.println("Nothing cancelled.");
            return;
        }
        sub.cancel(LocalDate.now());
        save.run();
        System.out.println("Cancelled \"" + sub.getName() + "\". You'll save " + money(sub.getMonthlyCost())
                + " a month (" + money(sub.getYearlyCost()) + " a year).");
    }

    private void removeSubscription() {
        Optional<Subscription> found = pickSubscription("remove");
        if (found.isEmpty()) {
            return;
        }
        Subscription sub = found.get();
        String answer = console.prompt("Remove \"" + sub.getName() + "\"? (y/n)");
        if (answer != null && answer.equalsIgnoreCase("y")) {
            manager.remove(sub.getId());
            save.run();
            System.out.println("Removed \"" + sub.getName() + "\".");
        } else {
            System.out.println("Nothing removed.");
        }
    }

    private Optional<Subscription> pickSubscription(String action) {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions yet.");
            return Optional.empty();
        }
        printTable(manager.getAll());
        String input = console.prompt("ID of the subscription to " + action + " (blank to go back)");
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

    private static String dateLabel(boolean trial) {
        return trial ? "Trial end / first payment date (YYYY-MM-DD)" : "Next payment date (YYYY-MM-DD)";
    }
}
