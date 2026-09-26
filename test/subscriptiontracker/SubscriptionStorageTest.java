package subscriptiontracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubscriptionStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path file;
    private SubscriptionStorage storage;

    @Before
    public void setUp() {
        file = folder.getRoot().toPath().resolve("subscriptions.txt");
        storage = new SubscriptionStorage(file);
    }

    @Test
    public void missingFileLoadsNothing() throws IOException {
        SubscriptionManager manager = new SubscriptionManager();

        assertEquals(0, storage.load(manager));
        assertTrue(manager.isEmpty());
    }

    @Test
    public void saveThenLoadKeepsEveryField() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        original.add("Netflix", new BigDecimal("199.00"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming");
        original.add("Adobe Creative Cloud", new BigDecimal("2400.50"), BillingCycle.YEARLY,
                LocalDate.of(2027, 1, 15), "Software");
        storage.save(original);

        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));

        assertEquals(2, loaded.getAll().size());
        Subscription adobe = loaded.find(2).orElseThrow();
        assertEquals("Adobe Creative Cloud", adobe.getName());
        assertEquals(new BigDecimal("2400.50"), adobe.getCost());
        assertEquals(BillingCycle.YEARLY, adobe.getCycle());
        assertEquals(LocalDate.of(2027, 1, 15), adobe.getNextPayment());
        assertEquals("Software", adobe.getCategory());
    }

    @Test
    public void newIdsContinueAfterLoadedOnes() throws IOException {
        Files.write(file, List.of("5\tGym\t50\tWEEKLY\t2026-10-01\tHealth"), StandardCharsets.UTF_8);
        SubscriptionManager manager = new SubscriptionManager();
        storage.load(manager);

        Subscription added = manager.add("New", BigDecimal.ONE, BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Other");

        assertEquals(6, added.getId());
    }

    @Test
    public void unreadableLinesAreSkippedAndCounted() throws IOException {
        Files.write(file, List.of(
                "1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming",
                "",
                "not a subscription",
                "2\tBad cost\tabc\tMONTHLY\t2026-10-01\tX",
                "3\tBad cycle\t10\tDAILY\t2026-10-01\tX",
                "4\tBad date\t10\tMONTHLY\t01/10/2026\tX",
                "5\tSpotify\t59.99\tMONTHLY\t2026-09-28\tMusic"), StandardCharsets.UTF_8);
        SubscriptionManager manager = new SubscriptionManager();

        assertEquals(4, storage.load(manager));
        assertEquals(List.of("Spotify", "Netflix"),
                manager.getAll().stream().map(Subscription::getName).toList());
    }

    @Test
    public void saveOverwritesPreviousContents() throws IOException {
        SubscriptionManager manager = new SubscriptionManager();
        Subscription sub = manager.add("Netflix", new BigDecimal("199"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming");
        storage.save(manager);
        manager.remove(sub.getId());
        storage.save(manager);

        SubscriptionManager loaded = new SubscriptionManager();
        storage.load(loaded);
        assertTrue(loaded.isEmpty());
    }
}
