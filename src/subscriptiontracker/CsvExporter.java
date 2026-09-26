package subscriptiontracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes subscriptions to a CSV file that spreadsheet programs can open.
 */
public class CsvExporter {

    private static final String HEADER =
            "ID,Name,Category,Cost,Billing Cycle,Next Payment,Monthly Cost,Yearly Cost";

    private CsvExporter() {
    }

    public static void export(List<Subscription> subscriptions, Path file) throws IOException {
        Files.writeString(file, toCsv(subscriptions), StandardCharsets.UTF_8);
    }

    /** Builds the CSV text: a header row, then one row per subscription. */
    public static String toCsv(List<Subscription> subscriptions) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (Subscription s : subscriptions) {
            csv.append(String.join(",",
                    String.valueOf(s.getId()),
                    escape(s.getName()),
                    escape(s.getCategory()),
                    s.getCost().toPlainString(),
                    s.getCycle().getLabel(),
                    s.getNextPayment().toString(),
                    s.getMonthlyCost().toPlainString(),
                    s.getYearlyCost().toPlainString()))
                    .append("\r\n");
        }
        return csv.toString();
    }

    /** Quotes a value if it contains a comma, quote or line break. */
    static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
