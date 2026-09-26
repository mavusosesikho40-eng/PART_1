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
import static org.junit.Assert.fail;

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
    public void budgetIsSavedAsTheFirstLineAndLoadedBack() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        original.setMonthlyBudget(new BigDecimal("750.50"));
        original.add("Netflix", new BigDecimal("199.00"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming");
        storage.save(original);

        assertEquals("BUDGET\t750.50", Files.readAllLines(file, StandardCharsets.UTF_8).get(0));

        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));
        assertEquals(new BigDecimal("750.50"), loaded.getMonthlyBudget().orElseThrow());
        assertEquals(1, loaded.getAll().size());
    }

    @Test
    public void noBudgetLineWhenNoBudgetIsSet() throws IOException {
        SubscriptionManager manager = new SubscriptionManager();
        manager.add("Netflix", new BigDecimal("199.00"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming");
        storage.save(manager);

        assertEquals(List.of("1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming"),
                Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    public void unreadableBudgetLinesAreSkippedAndCounted() throws IOException {
        Files.write(file, List.of("BUDGET\tlots", "BUDGET",
                "1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming"), StandardCharsets.UTF_8);
        SubscriptionManager manager = new SubscriptionManager();

        assertEquals(2, storage.load(manager));
        assertTrue(manager.getMonthlyBudget().isEmpty());
        assertEquals(1, manager.getAll().size());
    }

    @Test
    public void freeTrialIsSavedAsAnExtraColumnAndLoadedBack() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        original.add("Paid", new BigDecimal("10"), BillingCycle.MONTHLY, LocalDate.of(2026, 10, 1), "X");
        original.add("Trial", new BigDecimal("20"), BillingCycle.MONTHLY, LocalDate.of(2026, 10, 2), "X")
                .setFreeTrial(true);
        storage.save(original);

        assertEquals(List.of("1\tPaid\t10\tMONTHLY\t2026-10-01\tX",
                "2\tTrial\t20\tMONTHLY\t2026-10-02\tX\tTRIAL"),
                Files.readAllLines(file, StandardCharsets.UTF_8));

        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));
        assertTrue(!loaded.find(1).orElseThrow().isFreeTrial());
        assertTrue(loaded.find(2).orElseThrow().isFreeTrial());
    }

    @Test
    public void unknownExtraColumnIsNotATrial() throws IOException {
        Files.write(file, List.of("1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming\tSOMETHING"),
                StandardCharsets.UTF_8);
        SubscriptionManager manager = new SubscriptionManager();

        assertEquals(0, storage.load(manager));
        assertTrue(!manager.find(1).orElseThrow().isFreeTrial());
    }

    @Test
    public void cancelledDateIsSavedAndLoadedBack() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        Subscription sub = original.add("Trial", new BigDecimal("20"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 2), "X");
        sub.setFreeTrial(true);
        sub.cancel(LocalDate.of(2026, 9, 20));
        storage.save(original);

        assertEquals(List.of("1\tTrial\t20\tMONTHLY\t2026-10-02\tX\tTRIAL\tCANCELLED=2026-09-20"),
                Files.readAllLines(file, StandardCharsets.UTF_8));

        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));
        Subscription back = loaded.find(1).orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 20), back.getCancelledOn());
        assertTrue(back.isFreeTrial());
    }

    @Test
    public void unreadableCancelledDateSkipsTheLine() throws IOException {
        Files.write(file, List.of("1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming\tCANCELLED=soon"),
                StandardCharsets.UTF_8);

        assertEquals(1, storage.load(new SubscriptionManager()));
    }

    private static SubscriptionManager oneSubscription(String name) {
        SubscriptionManager manager = new SubscriptionManager();
        manager.add(name, new BigDecimal("10"), BillingCycle.MONTHLY, LocalDate.of(2026, 10, 1), "X");
        return manager;
    }

    private Path sibling(String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }

    @Test
    public void firstSaveLeavesNoBackupOrTemporaryFile() throws IOException {
        storage.save(oneSubscription("Netflix"));

        assertTrue(Files.exists(file));
        assertTrue(!Files.exists(storage.getBackupFile()));
        assertTrue(!Files.exists(sibling(".tmp")));
    }

    @Test
    public void eachSaveKeepsThePreviousVersionAsABackup() throws IOException {
        storage.save(oneSubscription("First"));
        storage.save(oneSubscription("Second"));

        assertEquals(sibling(".bak"), storage.getBackupFile());
        assertEquals(List.of("1\tFirst\t10\tMONTHLY\t2026-10-01\tX"),
                Files.readAllLines(storage.getBackupFile(), StandardCharsets.UTF_8));
        assertEquals(List.of("1\tSecond\t10\tMONTHLY\t2026-10-01\tX"),
                Files.readAllLines(file, StandardCharsets.UTF_8));
        assertTrue(!Files.exists(sibling(".tmp")));
    }

    @Test
    public void failedSaveLeavesTheOriginalFileUntouched() throws IOException {
        storage.save(oneSubscription("Original"));
        // A directory where the temporary file should go makes writing it fail.
        Files.createDirectory(sibling(".tmp"));

        try {
            storage.save(oneSubscription("Replacement"));
            fail("expected the save to fail");
        } catch (IOException expected) {
            // The save failed, as intended.
        }

        assertEquals(List.of("1\tOriginal\t10\tMONTHLY\t2026-10-01\tX"),
                Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    public void leftoverTemporaryFileFromACrashIsReplaced() throws IOException {
        Files.writeString(sibling(".tmp"), "half-written junk from an earlier crash");

        storage.save(oneSubscription("Netflix"));

        assertEquals(List.of("1\tNetflix\t10\tMONTHLY\t2026-10-01\tX"),
                Files.readAllLines(file, StandardCharsets.UTF_8));
        assertTrue(!Files.exists(sibling(".tmp")));
    }

    @Test
    public void keepUnreadableCopyCopiesTheFileExactly() throws IOException {
        Files.writeString(file, "not a subscription\n1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming\n");

        Path copy = storage.keepUnreadableCopy();

        assertEquals(sibling(".unreadable"), copy);
        assertEquals(Files.readString(file), Files.readString(copy));
    }

    @Test
    public void priceChangesAreSavedAndLoadedBack() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        Subscription sub = original.add("Netflix", new BigDecimal("169.00"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming");
        sub.changePrice(new BigDecimal("199.00"), LocalDate.of(2026, 3, 1));
        sub.changePrice(new BigDecimal("229.00"), LocalDate.of(2026, 9, 1));
        storage.save(original);

        assertEquals(List.of("1\tNetflix\t229.00\tMONTHLY\t2026-10-01\tStreaming"
                + "\tPRICE=2026-03-01:169.00:199.00\tPRICE=2026-09-01:199.00:229.00"),
                Files.readAllLines(file, StandardCharsets.UTF_8));

        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));
        Subscription back = loaded.find(1).orElseThrow();
        assertEquals(new BigDecimal("229.00"), back.getCost());
        assertEquals(sub.getPriceChanges(), back.getPriceChanges());
    }

    @Test
    public void unreadablePriceChangeSkipsTheLine() throws IOException {
        Files.write(file, List.of("1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming\tPRICE=yesterday:1:2"),
                StandardCharsets.UTF_8);

        assertEquals(1, storage.load(new SubscriptionManager()));
    }

    @Test
    public void noteIsSavedAndLoadedBack() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        original.add("Netflix", new BigDecimal("199.00"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming").setNote("Card: Capitec, ends 4321; login=me@home");
        storage.save(original);

        assertEquals(List.of("1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming"
                + "\tNOTE=Card: Capitec, ends 4321; login=me@home"),
                Files.readAllLines(file, StandardCharsets.UTF_8));

        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));
        assertEquals("Card: Capitec, ends 4321; login=me@home", loaded.find(1).orElseThrow().getNote());
    }

    @Test
    public void currencySymbolIsSavedAndLoadedBack() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        original.setMonthlyBudget(new BigDecimal("500"));
        original.setCurrencySymbol("R");
        storage.save(original);

        assertEquals(List.of("BUDGET\t500", "CURRENCY\tR"), Files.readAllLines(file, StandardCharsets.UTF_8));

        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));
        assertEquals("R", loaded.getCurrencySymbol());
    }

    @Test
    public void readsAFileSavedInTheWindowsCharacterSet() throws IOException {
        // "Café Club" as Notepad's "ANSI" encoding saves it: é is the single byte 0xE9.
        Files.write(file, ("1\tCaf\u00e9 Club\t50.00\tMONTHLY\t2026-10-01\tFood\n")
                .getBytes(java.nio.charset.Charset.forName("windows-1252")));
        SubscriptionManager manager = new SubscriptionManager();

        assertEquals(0, storage.load(manager));
        assertEquals("Caf\u00e9 Club", manager.find(1).orElseThrow().getName());
    }

    @Test
    public void readsAUtf8FileWithAByteOrderMark() throws IOException {
        Files.writeString(file, "\uFEFF1\tNetflix\t199.00\tMONTHLY\t2026-10-01\tStreaming\n");
        SubscriptionManager manager = new SubscriptionManager();

        assertEquals(0, storage.load(manager));
        assertEquals("Netflix", manager.find(1).orElseThrow().getName());
    }

    @Test
    public void billingDayIsSavedOnlyWhenItDiffersFromTheDate() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        original.add("Gym", new BigDecimal("250.00"), BillingCycle.MONTHLY, LocalDate.of(2026, 1, 31), "Health")
                .rollForward(LocalDate.of(2026, 2, 10));
        original.add("Spotify", new BigDecimal("59.99"), BillingCycle.MONTHLY, LocalDate.of(2026, 2, 15), "Music");
        storage.save(original);

        assertEquals(List.of("2\tSpotify\t59.99\tMONTHLY\t2026-02-15\tMusic",
                "1\tGym\t250.00\tMONTHLY\t2026-02-28\tHealth\tDAY=31"),
                Files.readAllLines(file, StandardCharsets.UTF_8));

        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));
        Subscription gym = loaded.find(1).orElseThrow();
        assertEquals(31, gym.getBillingDay());
        gym.rollForward(LocalDate.of(2026, 3, 10));
        assertEquals(LocalDate.of(2026, 3, 31), gym.getNextPayment());
    }

    @Test
    public void priceChangeWithABillingCycleChangeIsSavedAndLoadedBack() throws IOException {
        SubscriptionManager original = new SubscriptionManager();
        original.add("Netflix", new BigDecimal("199.00"), BillingCycle.MONTHLY, LocalDate.of(2026, 10, 1), "Streaming")
                .changePrice(new BigDecimal("2000.00"), BillingCycle.YEARLY, LocalDate.of(2026, 9, 1));
        storage.save(original);

        assertTrue(Files.readString(file).contains("PRICE=2026-09-01:199.00:2000.00:MONTHLY:YEARLY"));
        SubscriptionManager loaded = new SubscriptionManager();
        assertEquals(0, storage.load(loaded));
        assertEquals(original.find(1).orElseThrow().getPriceChanges(), loaded.find(1).orElseThrow().getPriceChanges());
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
