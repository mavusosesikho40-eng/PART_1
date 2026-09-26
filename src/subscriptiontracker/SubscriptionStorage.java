package subscriptiontracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads subscriptions as a tab-separated text file.
 * Each line holds: id, name, cost, cycle, next payment date, category,
 * followed by optional flags: "TRIAL" for a free trial and
 * "CANCELLED=yyyy-mm-dd" for a cancelled subscription.
 * If a monthly budget is set, the first line is "BUDGET" and the amount.
 */
public class SubscriptionStorage {

    private static final String SEPARATOR = "\t";
    private static final String BUDGET = "BUDGET";
    private static final String TRIAL = "TRIAL";
    private static final String CANCELLED = "CANCELLED=";

    private final Path file;

    public SubscriptionStorage(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    public void save(SubscriptionManager manager) throws IOException {
        List<String> lines = new ArrayList<>();
        manager.getMonthlyBudget().ifPresent(budget ->
                lines.add(BUDGET + SEPARATOR + budget.toPlainString()));
        for (Subscription s : manager.getAllIncludingCancelled()) {
            String line = String.join(SEPARATOR,
                    String.valueOf(s.getId()),
                    s.getName(),
                    s.getCost().toPlainString(),
                    s.getCycle().name(),
                    s.getNextPayment().toString(),
                    s.getCategory());
            if (s.isFreeTrial()) {
                line += SEPARATOR + TRIAL;
            }
            if (s.isCancelled()) {
                line += SEPARATOR + CANCELLED + s.getCancelledOn();
            }
            lines.add(line);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /**
     * Loads saved subscriptions into the manager. Lines that cannot be read
     * are skipped and counted.
     *
     * @return the number of lines that were skipped
     */
    public int load(SubscriptionManager manager) throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        int skipped = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(SEPARATOR, -1);
            try {
                if (parts[0].equals(BUDGET)) {
                    manager.setMonthlyBudget(new BigDecimal(parts[1]));
                    continue;
                }
                Subscription sub = new Subscription(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        new BigDecimal(parts[2]),
                        BillingCycle.valueOf(parts[3]),
                        LocalDate.parse(parts[4]),
                        parts[5]);
                for (int i = 6; i < parts.length; i++) {
                    if (parts[i].equals(TRIAL)) {
                        sub.setFreeTrial(true);
                    } else if (parts[i].startsWith(CANCELLED)) {
                        sub.cancel(LocalDate.parse(parts[i].substring(CANCELLED.length())));
                    }
                }
                manager.restore(sub);
            } catch (RuntimeException e) {
                skipped++;
            }
        }
        return skipped;
    }
}
