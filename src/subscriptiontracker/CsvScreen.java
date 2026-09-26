package subscriptiontracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * The "Import or export CSV" menu.
 */
class CsvScreen {

    private static final String CSV_FILE = "subscriptions.csv";
    private static final int PROBLEMS_SHOWN = 10;

    private final SubscriptionManager manager;
    private final Console console;
    private final Runnable save;
    private final MoneyScreen money;

    CsvScreen(SubscriptionManager manager, Console console, Runnable save, MoneyScreen money) {
        this.manager = manager;
        this.console = console;
        this.save = save;
        this.money = money;
    }

    void menu() {
        switch (console.choose("Import or export CSV",
                "Export to CSV",
                "Import from CSV")) {
            case 1 -> exportToCsv();
            case 2 -> importFromCsv();
            default -> {
                // Back to the main menu.
            }
        }
    }

    private void exportToCsv() {
        if (manager.isEmpty()) {
            System.out.println("You have no subscriptions to export.");
            return;
        }
        String name = console.readText("File to export to", CSV_FILE);
        try {
            Path file = Path.of(name);
            CsvExporter.export(manager.getAll(), file);
            System.out.println("Exported " + manager.getAll().size() + " subscription(s) to "
                    + file.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            System.out.println("Could not export to " + name + ": " + e.getMessage());
        }
    }

    private void importFromCsv() {
        System.out.println("The first row must name the columns. Name and Cost are needed; Billing Cycle, "
                + "Next Payment, Category, Free Trial and Note are optional.");
        String name = console.readText("File to import from", CSV_FILE);
        CsvImporter.Result result;
        try {
            Path file = Path.of(name);
            if (!Files.isRegularFile(file)) {
                System.out.println("There's no file called " + file.toAbsolutePath() + ".");
                return;
            }
            result = CsvImporter.importFile(file, manager, LocalDate.now());
        } catch (IOException | RuntimeException e) {
            System.out.println("Could not import from " + name + ": " + e.getMessage());
            return;
        }
        if (result.imported() > 0) {
            save.run();
        }
        System.out.println("Imported " + result.imported() + " subscription(s).");
        List<String> problems = result.problems();
        if (!problems.isEmpty()) {
            System.out.println("Skipped " + problems.size() + " row(s):");
            problems.stream().limit(PROBLEMS_SHOWN).forEach(p -> System.out.println("  " + p));
            if (problems.size() > PROBLEMS_SHOWN) {
                System.out.println("  ...and " + (problems.size() - PROBLEMS_SHOWN) + " more.");
            }
        }
        if (result.imported() > 0) {
            money.showBudgetWarning();
        }
    }
}
