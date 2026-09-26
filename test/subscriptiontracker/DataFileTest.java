package subscriptiontracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataFileTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path file;
    private DataFile data;

    @Before
    public void setUp() {
        file = folder.getRoot().toPath().resolve("subscriptions.txt");
        data = new DataFile(new SubscriptionStorage(file));
    }

    private void seed(String... lines) throws IOException {
        Files.write(file, String.join("\n", lines).concat("\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void missingFileLoadsNothingAndReportsNoProblems() {
        DataFile.LoadResult result = data.load();

        assertEquals(new DataFile.LoadResult(0, null, null, null, null), result);
        assertTrue(data.manager().isEmpty());
        assertFalse(data.isUnreadable());
    }

    @Test
    public void overduePaymentsAreRolledForwardAndSaved() throws IOException {
        LocalDate overdue = LocalDate.now().minusDays(3);
        seed("1\tNetflix\t199.00\tMONTHLY\t" + overdue + "\tStreaming");

        data.load();

        LocalDate next = data.manager().find(1).orElseThrow().getNextPayment();
        assertEquals(overdue.plusMonths(1), next);
        assertTrue(Files.readString(file).contains(next.toString()));
    }

    @Test
    public void unreadableLinesAreCopiedBeforeAnythingIsSaved() throws IOException {
        seed("this line is broken", "1\tNetflix\t199.00\tMONTHLY\t" + LocalDate.now().plusDays(5) + "\tStreaming");

        DataFile.LoadResult result = data.load();

        assertEquals(1, result.skippedLines());
        assertNotNull(result.unreadableCopy());
        assertTrue(Files.readString(result.unreadableCopy()).contains("this line is broken"));
        assertEquals(1, data.manager().getAll().size());
    }

    @Test
    public void aFileThatCantBeReadIsNeverSaved() throws IOException {
        Files.createDirectory(file);

        DataFile.LoadResult result = data.load();
        data.manager().add("Gym", new BigDecimal("250"), BillingCycle.MONTHLY, LocalDate.now(), "Health");

        assertNotNull(result.readError());
        assertTrue(data.isUnreadable());
        try {
            data.save();
            fail("saving should be refused");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("hasn't been overwritten"));
        }
        assertTrue(Files.isDirectory(file));
    }

    @Test
    public void savedChangesAreThereWhenLoadedAgain() throws IOException {
        data.load();
        data.manager().add("Gym", new BigDecimal("250"), BillingCycle.MONTHLY, LocalDate.now().plusDays(2), "Health");
        data.manager().setMonthlyBudget(new BigDecimal("500"));
        data.save();

        DataFile again = new DataFile(new SubscriptionStorage(file));
        DataFile.LoadResult result = again.load();

        assertNull(result.readError());
        assertEquals("Gym", again.manager().getAll().get(0).getName());
        assertEquals(0, new BigDecimal("500").compareTo(again.manager().getMonthlyBudget().orElseThrow()));
    }
}
