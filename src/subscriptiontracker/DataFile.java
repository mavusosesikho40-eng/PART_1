package subscriptiontracker;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * The subscriptions together with the file they're saved in, shared by the
 * console app and the window.
 *
 * <p>Loading rolls overdue payment dates forward. If loading finds lines it
 * can't read, the file is first copied as it was, so those lines aren't lost.
 * If the file exists but can't be read at all, saving is refused from then
 * on, so the file is never replaced by the empty list the app started with.
 */
public class DataFile {

    /**
     * What happened when loading.
     *
     * @param skippedLines lines that couldn't be read and were left out
     * @param unreadableCopy where the file was copied because of those lines, or null
     * @param copyError why that copy couldn't be made, or null
     * @param readError why the file couldn't be read at all, or null
     * @param saveError why the rolled-forward dates couldn't be saved, or null
     */
    public record LoadResult(int skippedLines, Path unreadableCopy, String copyError,
            String readError, String saveError) {
    }

    private final SubscriptionStorage storage;
    private final SubscriptionManager manager = new SubscriptionManager();
    private boolean unreadable;

    public DataFile(SubscriptionStorage storage) {
        this.storage = storage;
    }

    public SubscriptionManager manager() {
        return manager;
    }

    public Path file() {
        return storage.getFile();
    }

    /** True if the file couldn't be read when loading, so nothing will be saved. */
    public boolean isUnreadable() {
        return unreadable;
    }

    public LoadResult load() {
        int skipped;
        try {
            skipped = storage.load(manager);
        } catch (IOException e) {
            unreadable = true;
            return new LoadResult(0, null, null, e.getMessage(), null);
        }
        Path copy = null;
        String copyError = null;
        if (skipped > 0) {
            try {
                copy = storage.keepUnreadableCopy();
            } catch (IOException e) {
                copyError = e.getMessage();
            }
        }
        String saveError = null;
        if (manager.rollForwardAll(LocalDate.now()) > 0) {
            try {
                save();
            } catch (IOException e) {
                saveError = e.getMessage();
            }
        }
        return new LoadResult(skipped, copy, copyError, null, saveError);
    }

    /**
     * Saves every subscription, the budget and the currency.
     *
     * @throws IOException if saving failed, or if the file couldn't be read when loading
     */
    public void save() throws IOException {
        if (unreadable) {
            throw new IOException(storage.getFile() + " couldn't be read when the app started, "
                    + "so it hasn't been overwritten.");
        }
        storage.save(manager);
    }
}
