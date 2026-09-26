package subscriptiontracker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Drives the console app with scripted input and checks what it saves and prints.
 */
public class SubscriptionTrackerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private Locale originalLocale;
    private Path file;

    @Before
    public void setUp() {
        // Amounts are formatted with the default locale; pin it so the expected text is stable.
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        file = folder.getRoot().toPath().resolve("subscriptions.txt");
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        Locale.setDefault(originalLocale);
    }

    /** Runs the app, typing each given line followed by Enter. */
    private String run(String... lines) {
        String input = String.join("\n", lines) + "\n";
        new SubscriptionTracker(new SubscriptionStorage(file), new Scanner(input)).run();
        return output.toString(StandardCharsets.UTF_8);
    }

    private SubscriptionManager saved() throws IOException {
        SubscriptionManager manager = new SubscriptionManager();
        new SubscriptionStorage(file).load(manager);
        return manager;
    }

    private void seed(String... lines) throws IOException {
        Files.write(file, List.of(lines), StandardCharsets.UTF_8);
    }

    @Test
    public void addingASubscriptionSavesIt() throws IOException {
        LocalDate due = LocalDate.now().plusDays(10);

        String out = run("2", "Netflix", "199", "2", due.toString(), "Streaming", "0");

        assertTrue(out.contains("Added \"Netflix\" (#1)."));
        Subscription sub = saved().find(1).orElseThrow();
        assertEquals("Netflix", sub.getName());
        assertEquals(new BigDecimal("199.00"), sub.getCost());
        assertEquals(BillingCycle.MONTHLY, sub.getCycle());
        assertEquals(due, sub.getNextPayment());
        assertEquals("Streaming", sub.getCategory());
    }

    @Test
    public void invalidAnswersAreAskedAgain() throws IOException {
        LocalDate due = LocalDate.now().plusDays(10);

        String out = run("2", "Adobe", "abc", "-5", "2400", "9", "4", "15/01/2027", due.toString(), "", "0");

        assertTrue(out.contains("Please enter a positive amount"));
        assertTrue(out.contains("Please choose 1-4."));
        assertTrue(out.contains("Please use the format YYYY-MM-DD"));
        Subscription sub = saved().find(1).orElseThrow();
        assertEquals(new BigDecimal("2400.00"), sub.getCost());
        assertEquals(BillingCycle.YEARLY, sub.getCycle());
        assertEquals("Other", sub.getCategory());
    }

    @Test
    public void blankNameCancelsAdding() throws IOException {
        run("2", "", "0");

        assertTrue(saved().isEmpty());
    }

    @Test
    public void addingWithAPastDateRollsItForward() throws IOException {
        run("2", "Gym", "50", "1", LocalDate.now().minusDays(20).toString(), "Health", "0");

        LocalDate next = saved().find(1).orElseThrow().getNextPayment();
        assertTrue(!next.isBefore(LocalDate.now()) && next.isBefore(LocalDate.now().plusWeeks(1)));
    }

    @Test
    public void editingKeepsBlankFieldsAndChangesTheRest() throws IOException {
        LocalDate due = LocalDate.now().plusDays(5);
        seed("1\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic");

        run("3", "1", "", "69.99", "", "", "", "0");

        Subscription sub = saved().find(1).orElseThrow();
        assertEquals("Spotify", sub.getName());
        assertEquals(new BigDecimal("69.99"), sub.getCost());
        assertEquals(BillingCycle.MONTHLY, sub.getCycle());
        assertEquals(due, sub.getNextPayment());
        assertEquals("Music", sub.getCategory());
    }

    @Test
    public void removingAsksForConfirmation() throws IOException {
        LocalDate due = LocalDate.now().plusDays(5);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming",
                "2\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic");

        run("4", "1", "n", "4", "2", "y", "0");

        SubscriptionManager manager = saved();
        assertTrue(manager.find(1).isPresent());
        assertTrue(manager.find(2).isEmpty());
    }

    @Test
    public void unknownIdIsReported() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(5) + "\tStreaming");

        String out = run("4", "42", "0");

        assertTrue(out.contains("No subscription with ID 42."));
        assertTrue(saved().find(1).isPresent());
    }

    @Test
    public void startupShowsPaymentsDueThisWeek() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(2) + "\tStreaming",
                "2\tAdobe\t2400.00\tYEARLY\t" + LocalDate.now().plusDays(60) + "\tSoftware");

        String out = run("0");

        assertTrue(out.contains("Payments due in the next 7 days:"));
        assertTrue(out.contains("in 2 days"));
        assertTrue(!out.contains("Adobe"));
    }

    @Test
    public void startupRollsOverdueDatesForwardAndSaves() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().minusMonths(2).minusDays(1) + "\tStreaming");

        run("0");

        assertTrue(!saved().find(1).orElseThrow().getNextPayment().isBefore(LocalDate.now()));
    }

    @Test
    public void spendingSummaryShowsTotals() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming",
                "2\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware");

        String out = run("6", "0");

        assertTrue(out.contains("Monthly total: 399.00"));
        assertTrue(out.contains("Yearly total:  4,788.00"));
    }

    @Test
    public void searchByCategoryListsMatchesWithTotals() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming",
                "2\tShowmax\t99.00\tMONTHLY\t" + due + "\tstreaming",
                "3\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware");

        String out = run("7", "stream", "0");

        assertTrue(out.contains("Categories: Software, Streaming"));
        assertTrue(out.contains("Netflix"));
        assertTrue(out.contains("Showmax"));
        assertTrue(!out.contains("Adobe"));
        assertTrue(out.contains("2 found. Monthly: 298.00  Yearly: 3,576.00"));
    }

    @Test
    public void searchByCategoryReportsNoMatches() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("7", "Gaming", "0");

        assertTrue(out.contains("No subscriptions in a category matching \"Gaming\"."));
    }

    @Test
    public void exportWritesCsvToTheChosenFile() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming");
        Path csv = folder.getRoot().toPath().resolve("export.csv");

        String out = run("8", csv.toString(), "0");

        assertTrue(out.contains("Exported 1 subscription(s) to"));
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertEquals("1,Netflix,Streaming,199.00,Monthly," + due + ",199.00,2388.00", lines.get(1));
    }

    @Test
    public void exportWithNoSubscriptionsWritesNothing() {
        String out = run("8", "0");

        assertTrue(out.contains("You have no subscriptions to export."));
    }

    @Test
    public void endOfInputExitsWithoutError() {
        String out = run("1");

        assertTrue(out.contains("You have no subscriptions yet."));
    }
}
