package subscriptiontracker;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Scanner;

/**
 * Console app for keeping track of subscriptions and what they cost.
 * This class runs the main menu, loads and saves the data file, and hands
 * each menu option to the screen that deals with it.
 */
public class SubscriptionTracker {

    private static final String DATA_FILE = "subscriptions.txt";

    private final DataFile data;
    private final SubscriptionManager manager;
    private final Console console;
    private final MoneyScreen money;
    private final ManageScreen manage;
    private final UpcomingScreen upcoming;
    private final FindScreen find;
    private final CsvScreen csv;

    public SubscriptionTracker(SubscriptionStorage storage, Scanner in) {
        this.data = new DataFile(storage);
        this.manager = data.manager();
        this.console = new Console(in);
        this.money = new MoneyScreen(manager, console, this::save);
        this.manage = new ManageScreen(manager, console, this::save, money);
        this.upcoming = new UpcomingScreen(manager, console);
        this.find = new FindScreen(manager, console);
        this.csv = new CsvScreen(manager, console, this::save, money);
    }

    public static void main(String[] args) {
        Path file = Path.of(args.length > 0 ? args[0] : DATA_FILE);
        new SubscriptionTracker(new SubscriptionStorage(file), new Scanner(System.in)).run();
    }

    public void run() {
        load();
        Display.setCurrency(manager.getCurrencySymbol());
        System.out.println("=== Subscription Tracker ===");
        upcoming.showUpcoming(7);
        upcoming.showTrialReminders(7);
        money.showBudgetWarning();

        while (true) {
            printMenu();
            String choice = console.prompt("Choose an option");
            if (choice == null) {
                return;
            }
            switch (choice) {
                case "1" -> manage.listAll();
                case "2" -> manage.addSubscription();
                case "3" -> manage.changeMenu();
                case "4" -> upcoming.menu();
                case "5" -> find.menu();
                case "6" -> money.menu();
                case "7" -> csv.menu();
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
        System.out.println("3. Edit, cancel or remove a subscription");
        System.out.println("4. Upcoming payments and free trials");
        System.out.println("5. Search, filter and sort");
        System.out.println("6. Spending, budget and savings");
        System.out.println("7. Import or export CSV");
        System.out.println("0. Exit");
    }

    // ---- Persistence ----

    private void load() {
        DataFile.LoadResult result = data.load();
        if (result.readError() != null) {
            System.out.println("Could not read " + data.file() + ": " + result.readError());
            System.out.println("To keep it safe, your changes won't be saved until the app is restarted "
                    + "with a file it can read.");
            return;
        }
        if (result.skippedLines() > 0) {
            System.out.println("Warning: skipped " + result.skippedLines() + " unreadable line(s) in " + data.file());
            if (result.unreadableCopy() != null) {
                System.out.println("The file as it was has been copied to " + result.unreadableCopy()
                        + ", so those lines aren't lost.");
            } else {
                System.out.println("Could not copy " + data.file() + ": " + result.copyError());
            }
        }
        if (result.saveError() != null) {
            System.out.println("Could not save to " + data.file() + ": " + result.saveError());
        }
    }

    private void save() {
        if (data.isUnreadable()) {
            System.out.println("Not saved: " + data.file() + " couldn't be read when the app started, "
                    + "so it hasn't been overwritten.");
            return;
        }
        try {
            data.save();
        } catch (IOException e) {
            System.out.println("Could not save to " + data.file() + ": " + e.getMessage());
        }
    }
}
