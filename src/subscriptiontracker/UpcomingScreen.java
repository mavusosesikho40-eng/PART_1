package subscriptiontracker;

import static subscriptiontracker.Display.displayName;
import static subscriptiontracker.Display.dueIn;
import static subscriptiontracker.Display.money;
import static subscriptiontracker.Display.shorten;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The "Upcoming payments and free trials" menu, plus the reminders shown
 * when the app starts.
 */
class UpcomingScreen {

    private final SubscriptionManager manager;
    private final Console console;

    UpcomingScreen(SubscriptionManager manager, Console console) {
        this.manager = manager;
        this.console = console;
    }

    void menu() {
        switch (console.choose("Upcoming payments and free trials",
                "Upcoming payments",
                "Free trials")) {
            case 1 -> upcomingPayments();
            case 2 -> listFreeTrials();
            default -> {
                // Back to the main menu.
            }
        }
    }

    private void upcomingPayments() {
        String input = console.prompt("Show payments due in the next how many days? [30]");
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

    void showUpcoming(int days) {
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

    /** Reminds about free trials that end soon, before they start charging. */
    void showTrialReminders(int days) {
        for (Subscription s : manager.getTrialsEndingWithin(LocalDate.now(), days)) {
            System.out.println("Reminder: your " + s.getName() + " free trial ends " + dueIn(s.getNextPayment())
                    + " (" + s.getNextPayment() + "). You'll be charged " + money(s.getCost())
                    + " unless you cancel.");
        }
    }
}
