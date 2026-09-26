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
 * Each line holds: id, name, cost, cycle, next payment date, category.
 */
public class SubscriptionStorage {

    private static final String SEPARATOR = "\t";

    private final Path file;

    public SubscriptionStorage(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    public void save(SubscriptionManager manager) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Subscription s : manager.getAll()) {
            lines.add(String.join(SEPARATOR,
                    String.valueOf(s.getId()),
                    s.getName(),
                    s.getCost().toPlainString(),
                    s.getCycle().name(),
                    s.getNextPayment().toString(),
                    s.getCategory()));
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
                manager.restore(new Subscription(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        new BigDecimal(parts[2]),
                        BillingCycle.valueOf(parts[3]),
                        LocalDate.parse(parts[4]),
                        parts[5]));
            } catch (RuntimeException e) {
                skipped++;
            }
        }
        return skipped;
    }
}
