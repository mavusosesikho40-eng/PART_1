package subscriptiontracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Scanner;

/**
 * Reads answers typed at the console, asking again until they make sense.
 */
class Console {

    private final Scanner in;

    Console(Scanner in) {
        this.in = in;
    }

    /** Prints a prompt and reads a trimmed line, or null if input has ended. */
    String prompt(String message) {
        System.out.print(message + ": ");
        if (!in.hasNextLine()) {
            System.out.println();
            return null;
        }
        return in.nextLine().trim();
    }

    /**
     * Shows a titled, numbered list of options and reads a choice.
     *
     * @return the chosen option, counting from 1, or 0 to go back
     */
    int choose(String title, String... options) {
        System.out.println("-- " + title + " --");
        for (int i = 0; i < options.length; i++) {
            System.out.println("  " + (i + 1) + ". " + options[i]);
        }
        String input = prompt("Choose (blank to go back)");
        if (input == null || input.isEmpty()) {
            return 0;
        }
        try {
            int choice = Integer.parseInt(input);
            if (choice >= 1 && choice <= options.length) {
                return choice;
            }
        } catch (NumberFormatException e) {
            // fall through to the message below
        }
        System.out.println("Please choose 1-" + options.length + ".");
        return 0;
    }

    private static String withDefault(String label, Object current) {
        return current == null ? label : label + " [" + current + "]";
    }

    /** Reads text; blank input returns the current value (null means cancel). */
    String readText(String label, String current) {
        String input = prompt(withDefault(label, current));
        if (input == null || input.isEmpty()) {
            return current;
        }
        return input.replace("\t", " ");
    }

    /** Reads a yes/no answer; blank keeps the current answer. */
    boolean readYesNo(String label, boolean current) {
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

    BigDecimal readCost(String label, BigDecimal current) {
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

    BillingCycle readCycle(BillingCycle current) {
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

    LocalDate readDate(String label, LocalDate current) {
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
}
