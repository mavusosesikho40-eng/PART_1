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

        String out = run("2", "Netflix", "199", "2", "", due.toString(), "Streaming", "0");

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

        String out = run("2", "Adobe", "abc", "-5", "2400", "9", "4", "", "15/01/2027", due.toString(), "", "0");

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
        run("2", "Gym", "50", "1", "", LocalDate.now().minusDays(20).toString(), "Health", "0");

        LocalDate next = saved().find(1).orElseThrow().getNextPayment();
        assertTrue(!next.isBefore(LocalDate.now()) && next.isBefore(LocalDate.now().plusWeeks(1)));
    }

    @Test
    public void editingKeepsBlankFieldsAndChangesTheRest() throws IOException {
        LocalDate due = LocalDate.now().plusDays(5);
        seed("1\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic");

        // The blank answer after "69.99" accepts recording it as a price change.
        String out = run("3", "1", "1", "", "69.99", "", "", "", "", "", "0");

        Subscription sub = saved().find(1).orElseThrow();
        assertEquals("Spotify", sub.getName());
        assertEquals(new BigDecimal("69.99"), sub.getCost());
        assertEquals(BillingCycle.MONTHLY, sub.getCycle());
        assertEquals(due, sub.getNextPayment());
        assertEquals("Music", sub.getCategory());
        assertTrue(out.contains("Price change recorded: 59.99 -> 69.99 (+10.00, +17%)."));
        assertEquals(1, sub.getPriceChanges().size());
    }

    @Test
    public void fixingATypoInTheCostIsNotRecordedAsAPriceChange() throws IOException {
        seed("1\tSpotify\t599.90\tMONTHLY\t" + LocalDate.now().plusDays(5) + "\tMusic");

        String out = run("3", "1", "1", "", "59.99", "n", "", "", "", "", "0");

        assertTrue(!out.contains("Price change recorded"));
        Subscription sub = saved().find(1).orElseThrow();
        assertEquals(new BigDecimal("59.99"), sub.getCost());
        assertTrue(sub.getPriceChanges().isEmpty());
    }

    @Test
    public void unchangedCostDoesNotAskAboutPriceChanges() throws IOException {
        seed("1\tSpotify\t59.99\tMONTHLY\t" + LocalDate.now().plusDays(5) + "\tMusic");

        String out = run("3", "1", "1", "Spotify Premium", "", "", "", "", "", "0");

        assertTrue(!out.contains("Record this as a price change?"));
        assertEquals("Spotify Premium", saved().find(1).orElseThrow().getName());
    }

    @Test
    public void priceChangesListShowsChangesAndTheYearlyEffect() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        LocalDate changed = LocalDate.now().minusMonths(2);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming\tPRICE=" + changed + ":169.00:199.00",
                "2\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware\tPRICE=" + changed.minusYears(2)
                        + ":2000.00:2400.00");

        String out = run("6", "4", "0");

        assertTrue(out.contains(changed + "   Netflix              169.00 -> 199.00 (+30.00, +18%)"));
        assertTrue(out.contains("2,000.00 -> 2,400.00 (+400.00, +20%)"));
        // Only the Netflix rise was in the last 12 months.
        assertTrue(out.contains("Price changes in the last 12 months: +30.00 a month (+360.00 a year)."));
    }

    @Test
    public void priceChangesListWhenThereAreNone() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("6", "4", "0");

        assertTrue(out.contains("No price changes recorded yet."));
    }

    @Test
    public void removingAsksForConfirmation() throws IOException {
        LocalDate due = LocalDate.now().plusDays(5);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming",
                "2\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic");

        run("3", "3", "1", "n", "3", "3", "2", "y", "0");

        SubscriptionManager manager = saved();
        assertTrue(manager.find(1).isPresent());
        assertTrue(manager.find(2).isEmpty());
    }

    @Test
    public void unknownIdIsReported() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(5) + "\tStreaming");

        String out = run("3", "3", "42", "0");

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

        String out = run("6", "1", "0");

        assertTrue(out.contains("Monthly total: 399.00"));
        assertTrue(out.contains("Yearly total:  4,788.00"));
    }

    @Test
    public void searchByCategoryListsMatchesWithTotals() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming",
                "2\tShowmax\t99.00\tMONTHLY\t" + due + "\tstreaming",
                "3\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware");

        String out = run("5", "1", "stream", "0");

        assertTrue(out.contains("Categories: Software, Streaming"));
        assertTrue(out.contains("Netflix"));
        assertTrue(out.contains("Showmax"));
        assertTrue(!out.contains("Adobe"));
        assertTrue(out.contains("2 found. Monthly: 298.00  Yearly: 3,576.00"));
    }

    @Test
    public void searchByCategoryReportsNoMatches() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("5", "1", "Gaming", "0");

        assertTrue(out.contains("No subscriptions in a category matching \"Gaming\"."));
    }

    @Test
    public void exportWritesCsvToTheChosenFile() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming");
        Path csv = folder.getRoot().toPath().resolve("export.csv");

        String out = run("7", csv.toString(), "0");

        assertTrue(out.contains("Exported 1 subscription(s) to"));
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertEquals("1,Netflix,Streaming,199.00,Monthly," + due + ",199.00,2388.00,No", lines.get(1));
    }

    @Test
    public void exportWithNoSubscriptionsWritesNothing() {
        String out = run("7", "0");

        assertTrue(out.contains("You have no subscriptions to export."));
    }

    @Test
    public void sortByCostShowsMostExpensiveFirstByDefault() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic",
                "2\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware",
                "3\tGym\t250.00\tMONTHLY\t" + due + "\tHealth");

        String out = run("5", "3", "1", "", "0");

        assertTrue(out.indexOf("Gym") < out.indexOf("Adobe"));
        assertTrue(out.indexOf("Adobe") < out.indexOf("Spotify"));
        assertTrue(out.contains("Sorted by cost per month, most expensive first."));
    }

    @Test
    public void sortByCostCanShowCheapestFirst() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic",
                "2\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware");

        String out = run("5", "3", "1", "2", "0");

        assertTrue(out.indexOf("Spotify") < out.indexOf("Adobe"));
        assertTrue(out.contains("Sorted by cost per month, cheapest first."));
    }

    @Test
    public void sortByNextPaymentShowsSoonestFirstByDefault() throws IOException {
        seed("1\tAdobe\t2400.00\tYEARLY\t" + LocalDate.now().plusDays(90) + "\tSoftware",
                "2\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(12) + "\tStreaming");

        String out = run("5", "3", "2", "", "0");

        assertTrue(out.indexOf("Netflix") < out.indexOf("Adobe"));
        assertTrue(out.contains("in 12 days"));
        assertTrue(out.contains("in 90 days"));
        assertTrue(out.contains("Sorted by next payment date, soonest first."));
    }

    @Test
    public void sortByNextPaymentCanShowLatestFirst() throws IOException {
        seed("1\tAdobe\t2400.00\tYEARLY\t" + LocalDate.now().plusDays(90) + "\tSoftware",
                "2\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(12) + "\tStreaming");

        String out = run("5", "3", "2", "2", "0");

        assertTrue(out.indexOf("Adobe") < out.indexOf("Netflix"));
        assertTrue(out.contains("Sorted by next payment date, latest first."));
    }

    @Test
    public void sortByNameShowsAToZByDefault() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic",
                "2\tAdobe\t2400.00\tYEARLY\t" + due.plusDays(1) + "\tSoftware",
                "3\tNetflix\t199.00\tMONTHLY\t" + due.plusDays(2) + "\tStreaming");

        String out = run("5", "3", "3", "", "0");

        assertTrue(out.indexOf("Adobe") < out.indexOf("Netflix"));
        assertTrue(out.indexOf("Netflix") < out.indexOf("Spotify"));
        assertTrue(out.contains("Sorted by name, A to Z."));
    }

    @Test
    public void sortByNameCanShowZToA() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware",
                "2\tSpotify\t59.99\tMONTHLY\t" + due.plusDays(1) + "\tMusic");

        String out = run("5", "3", "3", "2", "0");

        assertTrue(out.indexOf("Spotify") < out.indexOf("Adobe"));
        assertTrue(out.contains("Sorted by name, Z to A."));
    }

    @Test
    public void sortByCategoryShowsAToZByDefault() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware",
                "2\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic",
                "3\tGym\t250.00\tMONTHLY\t" + due + "\tHealth");

        String out = run("5", "3", "4", "", "0");

        assertTrue(out.indexOf("Gym") < out.indexOf("Spotify"));
        assertTrue(out.indexOf("Spotify") < out.indexOf("Adobe"));
        assertTrue(out.contains("Sorted by category, A to Z."));
    }

    @Test
    public void sortByCategoryCanShowZToA() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tGym\t250.00\tMONTHLY\t" + due + "\tHealth",
                "2\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware");

        String out = run("5", "3", "4", "2", "0");

        assertTrue(out.indexOf("Adobe") < out.indexOf("Gym"));
        assertTrue(out.contains("Sorted by category, Z to A."));
    }

    @Test
    public void sortByCycleShowsShortestFirstByDefault() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware",
                "2\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic",
                "3\tCoffee\t20.00\tWEEKLY\t" + due + "\tFood");

        String out = run("5", "3", "5", "", "0");

        assertTrue(out.indexOf("Coffee") < out.indexOf("Spotify"));
        assertTrue(out.indexOf("Spotify") < out.indexOf("Adobe"));
        assertTrue(out.contains("Sorted by billing cycle, shortest first."));
    }

    @Test
    public void sortByCycleCanShowLongestFirst() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic",
                "2\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware");

        String out = run("5", "3", "5", "2", "0");

        assertTrue(out.indexOf("Adobe") < out.indexOf("Spotify"));
        assertTrue(out.contains("Sorted by billing cycle, longest first."));
    }

    @Test
    public void sortByIdShowsLowestFirstByDefault() throws IOException {
        seed("3\tAdobe\t2400.00\tYEARLY\t" + LocalDate.now().plusDays(20) + "\tSoftware",
                "1\tSpotify\t59.99\tMONTHLY\t" + LocalDate.now().plusDays(30) + "\tMusic",
                "2\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(25) + "\tStreaming");

        String out = run("5", "3", "6", "", "0");

        assertTrue(out.indexOf("Spotify") < out.indexOf("Netflix"));
        assertTrue(out.indexOf("Netflix") < out.indexOf("Adobe"));
        assertTrue(out.contains("Sorted by ID, lowest first."));
    }

    @Test
    public void sortByIdCanShowHighestFirst() throws IOException {
        seed("1\tSpotify\t59.99\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tMusic",
                "2\tAdobe\t2400.00\tYEARLY\t" + LocalDate.now().plusDays(30) + "\tSoftware");

        String out = run("5", "3", "6", "2", "0");

        assertTrue(out.indexOf("Adobe") < out.indexOf("Spotify"));
        assertTrue(out.contains("Sorted by ID, highest first."));
    }

    @Test
    public void sortMenuRejectsUnknownChoice() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("5", "3", "7", "0");

        assertTrue(out.contains("Please choose 1-6."));
        assertTrue(!out.contains("Sorted by"));
    }

    @Test
    public void sortMenuBlankCancels() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("5", "3", "", "0");

        assertTrue(!out.contains("Order [1]"));
        assertTrue(!out.contains("Sorted by"));
    }

    @Test
    public void sortMenuWithNoSubscriptions() {
        String out = run("5", "3", "0");

        assertTrue(out.contains("You have no subscriptions yet."));
        assertTrue(!out.contains("Sort by:"));
    }

    @Test
    public void settingABudgetSavesItAndWarnsWhenClose() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("6", "2", "200", "0");

        assertTrue(out.contains("You haven't set a monthly budget."));
        assertTrue(out.contains("Monthly budget set to 200.00."));
        assertTrue(out.contains("Heads up: you're close to your monthly budget. "
                + "200.00 a month; you're spending 199.00 (99%), 1.00 left."));
        assertEquals(new BigDecimal("200.00"), saved().getMonthlyBudget().orElseThrow());
    }

    @Test
    public void startupWarnsWhenOverBudget() throws IOException {
        seed("BUDGET\t150.00",
                "1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("0");

        assertTrue(out.contains("Warning: you're over your monthly budget. "
                + "150.00 a month; you're spending 199.00 (132%), 49.00 over."));
    }

    @Test
    public void noWarningWhenWellUnderBudget() throws IOException {
        seed("BUDGET\t1000.00",
                "1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("0");

        assertTrue(!out.contains("over your monthly budget"));
        assertTrue(!out.contains("close to your monthly budget"));
    }

    @Test
    public void addingASubscriptionThatGoesOverBudgetWarns() throws IOException {
        seed("BUDGET\t250.00",
                "1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("2", "Spotify", "59.99", "2", "", LocalDate.now().plusDays(20).toString(), "Music", "0");

        assertTrue(out.contains("Added \"Spotify\" (#2)."));
        assertTrue(out.contains("Warning: you're over your monthly budget. "
                + "250.00 a month; you're spending 258.99 (103%), 8.99 over."));
    }

    @Test
    public void spendingSummaryShowsBudgetUse() throws IOException {
        seed("BUDGET\t1000.00",
                "1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("6", "1", "0");

        assertTrue(out.contains("Budget:        1,000.00 a month; you're spending 199.00 (19%), 801.00 left."));
    }

    @Test
    public void blankKeepsTheBudgetAndZeroRemovesIt() throws IOException {
        seed("BUDGET\t500.00",
                "1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("6", "2", "", "6", "2", "0", "0");

        assertTrue(out.contains("Monthly budget unchanged."));
        assertTrue(out.contains("Monthly budget removed."));
        assertTrue(saved().getMonthlyBudget().isEmpty());
        assertTrue(saved().find(1).isPresent());
    }

    @Test
    public void addingAFreeTrialSavesItAndConfirmsTheEndDate() throws IOException {
        LocalDate ends = LocalDate.now().plusDays(14);

        String out = run("2", "Netflix", "199", "2", "y", ends.toString(), "Streaming", "0");

        assertTrue(out.contains("Trial end / first payment date (YYYY-MM-DD)"));
        assertTrue(out.contains("Its free trial ends " + ends + " (in 14 days). "
                + "You'll be reminded when it's a week away."));
        assertTrue(saved().find(1).orElseThrow().isFreeTrial());
    }

    @Test
    public void addingATrialEndingThisWeekSaysToCancelInTime() {
        LocalDate ends = LocalDate.now().plusDays(7);

        String out = run("2", "Netflix", "199", "2", "y", ends.toString(), "Streaming", "0");

        assertTrue(out.contains("Its free trial ends " + ends + " (in 7 days). "
                + "Cancel before then if you don't want to be charged."));
    }

    @Test
    public void freeTrialQuestionRejectsOtherAnswers() throws IOException {
        run("2", "Netflix", "199", "2", "maybe", "yes", LocalDate.now().plusDays(14).toString(), "", "0");

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Please answer y or n."));
        assertTrue(saved().find(1).orElseThrow().isFreeTrial());
    }

    @Test
    public void startupRemindsAboutTrialsEndingThisWeek() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(3) + "\tStreaming\tTRIAL",
                "2\tShowmax\t99.00\tMONTHLY\t" + LocalDate.now().plusDays(30) + "\tStreaming\tTRIAL");

        String out = run("0");

        assertTrue(out.contains("Reminder: your Netflix free trial ends in 3 days ("
                + LocalDate.now().plusDays(3) + "). You'll be charged 199.00 unless you cancel."));
        assertTrue(!out.contains("Showmax free trial"));
    }

    @Test
    public void freeTrialsMenuListsTrialsOnly() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming\tTRIAL",
                "2\tSpotify\t59.99\tMONTHLY\t" + LocalDate.now().plusDays(10) + "\tMusic");

        String out = run("4", "2", "0");

        assertTrue(out.contains("Trial ends"));
        assertTrue(out.contains("in 20 days"));
        assertTrue(!out.contains("Spotify"));
        assertTrue(out.contains("Cancel before the end date if you don't want to be charged."));
    }

    @Test
    public void freeTrialsMenuWithNoTrials() throws IOException {
        seed("1\tSpotify\t59.99\tMONTHLY\t" + LocalDate.now().plusDays(10) + "\tMusic");

        String out = run("4", "2", "0");

        assertTrue(out.contains("You have no free trials."));
    }

    @Test
    public void trialsAreLabelledInTheTable() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming\tTRIAL");

        String out = run("1", "0");

        assertTrue(out.contains("Netflix (trial)"));
    }

    @Test
    public void editingCanTurnATrialIntoAPaidSubscription() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming\tTRIAL");

        String out = run("3", "1", "1", "", "", "", "n", "", "", "0");

        assertTrue(out.contains("Free trial? (y/n) [y]"));
        assertTrue(!saved().find(1).orElseThrow().isFreeTrial());
    }

    @Test
    public void cancellingMovesASubscriptionToTheCancelledList() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming",
                "2\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic");

        String out = run("3", "2", "1", "y", "6", "1", "0");

        assertTrue(out.contains("Cancelled \"Netflix\". You'll save 199.00 a month (2,388.00 a year)."));
        assertTrue(out.contains("Monthly total: 59.99"));
        Subscription netflix = saved().find(1).orElseThrow();
        assertEquals(LocalDate.now(), netflix.getCancelledOn());
    }

    @Test
    public void answeringNoLeavesTheSubscriptionActive() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("3", "2", "1", "n", "0");

        assertTrue(out.contains("Nothing cancelled."));
        assertTrue(!saved().find(1).orElseThrow().isCancelled());
    }

    @Test
    public void cancelledListShowsSavingsSoFar() throws IOException {
        LocalDate skipped = LocalDate.now().minusDays(35);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + skipped + "\tStreaming\tCANCELLED=" + skipped.minusDays(5),
                "2\tSpotify\t59.99\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tMusic");

        String out = run("6", "3", "", "0");

        assertTrue(out.contains("Saved so far: 398.00"));
        assertTrue(out.contains("Cancelling these saves you 199.00 a month (2,388.00 a year)."));
        assertEquals(skipped, saved().find(1).orElseThrow().getNextPayment());
    }

    @Test
    public void cancelledListWhenNothingIsCancelled() {
        String out = run("6", "3", "0");

        assertTrue(out.contains("You haven't cancelled any subscriptions."));
    }

    @Test
    public void restoringMakesItActiveAgainWithAFutureDate() throws IOException {
        LocalDate skipped = LocalDate.now().minusDays(35);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + skipped + "\tStreaming\tCANCELLED=" + skipped.minusDays(5));

        String out = run("6", "3", "1", "1", "0");

        assertTrue(out.contains("Restored \"Netflix\"."));
        Subscription netflix = saved().find(1).orElseThrow();
        assertTrue(!netflix.isCancelled());
        assertTrue(!netflix.getNextPayment().isBefore(LocalDate.now()));
    }

    @Test
    public void cancelledSubscriptionsCannotBeEditedByTypingTheirId() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming\tCANCELLED=" + LocalDate.now(),
                "2\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic");

        String out = run("3", "1", "1", "0");

        assertTrue(out.contains("No subscription with ID 1."));
    }

    @Test
    public void filterByCycleShowsOnlyThatCycleWithTotals() throws IOException {
        LocalDate due = LocalDate.now().plusDays(20);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + due + "\tStreaming",
                "2\tSpotify\t59.99\tMONTHLY\t" + due + "\tMusic",
                "3\tAdobe\t2400.00\tYEARLY\t" + due + "\tSoftware");

        String out = run("5", "2", "2", "0");

        assertTrue(out.contains("Netflix"));
        assertTrue(out.contains("Spotify"));
        assertTrue(!out.contains("Adobe"));
        assertTrue(out.contains("2 found. Monthly: 258.99  Yearly: 3,107.88"));
    }

    @Test
    public void filterByCycleWithNoMatches() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("5", "2", "1", "0");

        assertTrue(out.contains("No weekly subscriptions."));
    }

    @Test
    public void filterByCycleBlankGoesBack() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("5", "2", "", "0");

        assertTrue(!out.contains("found."));
    }

    @Test
    public void unreadableLinesAreKeptInACopyEvenAfterSaving() throws IOException {
        seed("this line is broken",
                "1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("2", "Spotify", "59.99", "2", "", LocalDate.now().plusDays(20).toString(), "Music",
                "2", "Gym", "250", "2", "", LocalDate.now().plusDays(20).toString(), "Health", "0");

        assertTrue(out.contains("Warning: skipped 1 unreadable line(s)"));
        assertTrue(out.contains("so those lines aren't lost."));
        Path copy = file.resolveSibling(file.getFileName() + ".unreadable");
        assertTrue(Files.readAllLines(copy, StandardCharsets.UTF_8).contains("this line is broken"));
        assertEquals(3, saved().getAll().size());
    }

    @Test
    public void mainMenuGroupsTheOptions() {
        String out = run("0");

        assertTrue(out.contains("3. Edit, cancel or remove a subscription"));
        assertTrue(out.contains("4. Upcoming payments and free trials"));
        assertTrue(out.contains("5. Search, filter and sort"));
        assertTrue(out.contains("6. Spending, budget and savings"));
        assertTrue(out.contains("7. Export to CSV"));
        assertTrue(!out.contains("8. "));
    }

    @Test
    public void subMenuBlankGoesBackToTheMainMenu() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(20) + "\tStreaming");

        String out = run("6", "", "1", "0");

        assertTrue(out.contains("-- Spending, budget and savings --"));
        assertTrue(!out.contains("Monthly total"));
        // The "1" after the blank answer was read by the main menu, so the table is shown.
        assertTrue(out.contains("ID   Name"));
    }

    @Test
    public void subMenuRejectsUnknownChoice() {
        String out = run("3", "9", "0");

        assertTrue(out.contains("Please choose 1-3."));
    }

    @Test
    public void upcomingPaymentsShowsTheChosenNumberOfDays() throws IOException {
        seed("1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(10) + "\tStreaming",
                "2\tAdobe\t2400.00\tYEARLY\t" + LocalDate.now().plusDays(60) + "\tSoftware");

        String out = run("4", "1", "14", "0");

        assertTrue(out.contains("Payments due in the next 14 days:"));
        assertTrue(out.contains("Total due: 199.00"));
        assertTrue(!out.contains("Adobe"));
    }

    @Test
    public void endOfInputExitsWithoutError() {
        String out = run("1");

        assertTrue(out.contains("You have no subscriptions yet."));
    }
}
