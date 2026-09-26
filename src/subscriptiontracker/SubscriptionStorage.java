package subscriptiontracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads subscriptions as a tab-separated text file.
 * Each line holds: id, name, cost, cycle, next payment date, category,
 * followed by optional flags: "TRIAL" for a free trial and
 * "CANCELLED=yyyy-mm-dd" for a cancelled subscription, and one
 * "PRICE=yyyy-mm-dd:old:new" for each recorded price change.
 * If a monthly budget is set, the first line is "BUDGET" and the amount.
 *
 * <p>Saving never leaves a half-written file behind: the new contents are
 * written to a temporary file and flushed to disk, the previous file is kept
 * as a ".bak" copy, and only then is the new file moved into place.
 */
public class SubscriptionStorage {

    private static final String SEPARATOR = "\t";
    private static final String BUDGET = "BUDGET";
    private static final String TRIAL = "TRIAL";
    private static final String CANCELLED = "CANCELLED=";
    private static final String PRICE = "PRICE=";

    private final Path file;

    public SubscriptionStorage(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    /** The previous version of the data file, kept on every save. */
    public Path getBackupFile() {
        return sibling(".bak");
    }

    /**
     * Copies the data file, exactly as it is, to a ".unreadable" file next to
     * it. Used when loading found lines it couldn't read, so they are kept
     * even though the next save leaves them out.
     *
     * @return the copy
     */
    public Path keepUnreadableCopy() throws IOException {
        Path copy = sibling(".unreadable");
        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
        return copy;
    }

    private Path sibling(String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
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
            for (PriceChange change : s.getPriceChanges()) {
                line += SEPARATOR + PRICE + change.date() + ":" + change.oldCost().toPlainString()
                        + ":" + change.newCost().toPlainString();
            }
            lines.add(line);
        }
        writeSafely(lines);
    }

    private void writeSafely(List<String> lines) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(line).append(System.lineSeparator());
        }
        Path temp = sibling(".tmp");
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(text.toString().getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        if (Files.exists(file)) {
            Files.copy(file, getBackupFile(), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
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
                    } else if (parts[i].startsWith(PRICE)) {
                        String[] price = parts[i].substring(PRICE.length()).split(":", -1);
                        sub.restorePriceChange(new PriceChange(LocalDate.parse(price[0]),
                                new BigDecimal(price[1]), new BigDecimal(price[2])));
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
