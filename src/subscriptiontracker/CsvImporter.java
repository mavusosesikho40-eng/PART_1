package subscriptiontracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads subscriptions from a CSV file: one exported by this app, or a
 * spreadsheet saved as CSV.
 *
 * <p>The first row must name the columns. Only Name and Cost are required;
 * Billing Cycle (default monthly), Next Payment (default today), Category
 * (default "Other"), Free Trial and Note are optional, and any other
 * columns are ignored. Rows that can't be read, or whose name is already
 * in the list, are skipped with a reason.
 */
public final class CsvImporter {

    /** How many subscriptions were imported, and why any rows were skipped. */
    public record Result(int imported, List<String> problems) {
    }

    private static final Map<String, String> COLUMN_NAMES = new HashMap<>();

    static {
        column("name", "name", "subscription", "service");
        column("cost", "cost", "price", "amount");
        column("cycle", "billing cycle", "cycle", "billing", "frequency");
        column("next", "next payment", "next payment date", "next due", "due date", "next billing date");
        column("category", "category");
        column("trial", "free trial", "trial");
        column("note", "note", "notes");
    }

    private static void column(String field, String... headers) {
        for (String header : headers) {
            COLUMN_NAMES.put(header, field);
        }
    }

    private static final DateTimeFormatter YEAR_FIRST_SLASHES = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final DateTimeFormatter DAY_FIRST = DateTimeFormatter.ofPattern("d/M/yyyy");

    private CsvImporter() {
    }

    public static Result importFile(Path file, SubscriptionManager manager, LocalDate today) throws IOException {
        return importText(TextFiles.read(file), manager, today);
    }

    public static Result importText(String text, SubscriptionManager manager, LocalDate today) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = parse(text, detectDelimiter(text));
        List<String> problems = new ArrayList<>();
        if (rows.isEmpty()) {
            problems.add("The file is empty.");
            return new Result(0, problems);
        }

        Map<String, Integer> columns = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            String field = COLUMN_NAMES.get(header.get(i).strip().toLowerCase(Locale.ROOT));
            if (field != null) {
                columns.putIfAbsent(field, i);
            }
        }
        if (!columns.containsKey("name") || !columns.containsKey("cost")) {
            problems.add("The first row must name the columns, including Name and Cost.");
            return new Result(0, problems);
        }

        Set<String> names = new HashSet<>();
        for (Subscription s : manager.getAll()) {
            names.add(s.getName().toLowerCase(Locale.ROOT));
        }

        int imported = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            int rowNumber = r + 1;
            try {
                String name = cell(row, columns, "name").replace("\t", " ");
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("it has no name");
                }
                if (!names.add(name.toLowerCase(Locale.ROOT))) {
                    problems.add("Row " + rowNumber + ": \"" + name + "\" is already in your list.");
                    continue;
                }
                BigDecimal cost = parseAmount(cell(row, columns, "cost"));
                BillingCycle cycle = parseCycle(cell(row, columns, "cycle"));
                String date = cell(row, columns, "next");
                LocalDate next = date.isEmpty() ? today : parseDate(date);
                String category = cell(row, columns, "category").replace("\t", " ");

                Subscription sub = manager.add(name, cost, cycle, next, category.isEmpty() ? "Other" : category);
                sub.setFreeTrial(isYes(cell(row, columns, "trial")));
                sub.setNote(cell(row, columns, "note").replace("\t", " "));
                sub.rollForward(today);
                imported++;
            } catch (IllegalArgumentException e) {
                problems.add("Row " + rowNumber + ": " + e.getMessage() + ".");
            }
        }
        return new Result(imported, problems);
    }

    private static String cell(List<String> row, Map<String, Integer> columns, String field) {
        Integer index = columns.get(field);
        return index == null || index >= row.size() ? "" : row.get(index).strip();
    }

    /**
     * Reads an amount such as "199", "R 1,299.00", "$1 299.50", "1299,50" or
     * "1.299,50". When both a dot and a comma appear, whichever comes last is
     * the decimal point; a lone comma followed by one or two digits at the
     * end is a decimal comma; any other comma separates thousands.
     */
    public static BigDecimal parseAmount(String text) {
        String digits = text.replaceAll("[^0-9.,\\-]", "");
        int lastComma = digits.lastIndexOf(',');
        int lastDot = digits.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            digits = lastComma > lastDot
                    ? digits.replace(".", "").replace(',', '.')
                    : digits.replace(",", "");
        } else if (lastComma >= 0 && digits.matches(".*,\\d{1,2}")) {
            digits = digits.replace(',', '.');
        } else {
            digits = digits.replace(",", "");
        }
        try {
            BigDecimal amount = new BigDecimal(digits);
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("the cost \"" + text + "\" is negative");
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the cost \"" + text + "\" isn't an amount");
        }
    }

    static BillingCycle parseCycle(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "", "monthly", "month", "per month" -> BillingCycle.MONTHLY;
            case "weekly", "week", "per week" -> BillingCycle.WEEKLY;
            case "quarterly", "quarter", "per quarter" -> BillingCycle.QUARTERLY;
            case "yearly", "year", "annual", "annually", "per year" -> BillingCycle.YEARLY;
            default -> throw new IllegalArgumentException(
                    "the billing cycle \"" + text + "\" isn't weekly, monthly, quarterly or yearly");
        };
    }

    /** Reads a date written as 2026-10-01, 2026/10/01 or 01/10/2026 (day first). */
    public static LocalDate parseDate(String text) {
        for (DateTimeFormatter format : List.of(DateTimeFormatter.ISO_LOCAL_DATE, YEAR_FIRST_SLASHES, DAY_FIRST)) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        throw new IllegalArgumentException("the date \"" + text + "\" isn't like 2026-10-01 or 01/10/2026");
    }

    private static boolean isYes(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "yes", "y", "true", "1" -> true;
            default -> false;
        };
    }

    /** Picks whichever of comma, semicolon or tab appears most in the first line. */
    static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        char best = ',';
        long bestCount = firstLine.chars().filter(c -> c == ',').count();
        for (char candidate : new char[] {';', '\t'}) {
            long count = firstLine.chars().filter(c -> c == candidate).count();
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    /**
     * Splits CSV text into rows of cells. Quoted cells may contain the
     * delimiter, line breaks and doubled quotes ("") for a quote.
     */
    static List<List<String>> parse(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
