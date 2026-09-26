package subscriptiontracker;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;

public class CsvExporterTest {

    private static final String HEADER =
            "ID,Name,Category,Cost,Billing Cycle,Next Payment,Monthly Cost,Yearly Cost,Free Trial";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static Subscription sub(int id, String name, String cost, BillingCycle cycle, String category) {
        return new Subscription(id, name, new BigDecimal(cost), cycle, LocalDate.of(2026, 10, 1), category);
    }

    @Test
    public void emptyListGivesJustTheHeader() {
        assertEquals(HEADER + "\r\n", CsvExporter.toCsv(List.of()));
    }

    @Test
    public void writesOneRowPerSubscriptionWithCalculatedCosts() {
        String csv = CsvExporter.toCsv(List.of(
                sub(1, "Netflix", "199.00", BillingCycle.MONTHLY, "Streaming"),
                sub(2, "Adobe", "2400.00", BillingCycle.YEARLY, "Software")));

        assertEquals(HEADER + "\r\n"
                + "1,Netflix,Streaming,199.00,Monthly,2026-10-01,199.00,2388.00,No\r\n"
                + "2,Adobe,Software,2400.00,Yearly,2026-10-01,200.00,2400.00,No\r\n", csv);
    }

    @Test
    public void quotesValuesThatContainCommasOrQuotes() {
        String csv = CsvExporter.toCsv(List.of(
                sub(1, "Disney+, Hulu", "10", BillingCycle.MONTHLY, "The \"Best\" Shows")));

        assertEquals("1,\"Disney+, Hulu\",\"The \"\"Best\"\" Shows\",10,Monthly,2026-10-01,10.00,120,No",
                csv.split("\r\n")[1]);
    }

    @Test
    public void marksFreeTrials() {
        Subscription trial = sub(1, "Netflix", "199.00", BillingCycle.MONTHLY, "Streaming");
        trial.setFreeTrial(true);

        assertEquals("1,Netflix,Streaming,199.00,Monthly,2026-10-01,199.00,2388.00,Yes",
                CsvExporter.toCsv(List.of(trial)).split("\r\n")[1]);
    }

    @Test
    public void escapeLeavesPlainValuesAlone() {
        assertEquals("Streaming", CsvExporter.escape("Streaming"));
        assertEquals("\"a\nb\"", CsvExporter.escape("a\nb"));
    }

    @Test
    public void exportWritesTheFile() throws IOException {
        Path file = folder.getRoot().toPath().resolve("out.csv");
        List<Subscription> subs = List.of(sub(1, "Netflix", "199.00", BillingCycle.MONTHLY, "Streaming"));

        CsvExporter.export(subs, file);

        assertEquals(CsvExporter.toCsv(subs), Files.readString(file, StandardCharsets.UTF_8));
    }
}
