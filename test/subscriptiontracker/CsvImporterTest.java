package subscriptiontracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CsvImporterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private SubscriptionManager manager;

    @Before
    public void setUp() {
        manager = new SubscriptionManager();
    }

    private CsvImporter.Result importText(String text) {
        return CsvImporter.importText(text, manager, TODAY);
    }

    @Test
    public void importsTheAppsOwnExportFormat() {
        CsvImporter.Result result = importText(
                "ID,Name,Category,Cost,Billing Cycle,Next Payment,Monthly Cost,Yearly Cost,Free Trial,Note\r\n"
                + "1,Netflix,Streaming,199.00,Monthly,2026-10-01,199.00,2388.00,Yes,\"Family, shared\"\r\n"
                + "2,Adobe,Software,2400.00,Yearly,2027-01-15,200.00,2400.00,No,\r\n");

        assertEquals(2, result.imported());
        assertTrue(result.problems().isEmpty());
        Subscription netflix = manager.getAll().get(0);
        assertEquals("Netflix", netflix.getName());
        assertEquals("Streaming", netflix.getCategory());
        assertEquals(new BigDecimal("199.00"), netflix.getCost());
        assertEquals(BillingCycle.MONTHLY, netflix.getCycle());
        assertEquals(LocalDate.of(2026, 10, 1), netflix.getNextPayment());
        assertTrue(netflix.isFreeTrial());
        assertEquals("Family, shared", netflix.getNote());
        assertEquals(BillingCycle.YEARLY, manager.getAll().get(1).getCycle());
    }

    @Test
    public void onlyNameAndCostAreNeeded() {
        CsvImporter.Result result = importText("name,cost\nGym,250\n");

        assertEquals(1, result.imported());
        Subscription gym = manager.getAll().get(0);
        assertEquals(BillingCycle.MONTHLY, gym.getCycle());
        assertEquals(TODAY, gym.getNextPayment());
        assertEquals("Other", gym.getCategory());
        assertFalse(gym.isFreeTrial());
    }

    @Test
    public void readsSemicolonFilesWithOtherColumnNamesInAnyOrder() {
        CsvImporter.Result result = importText("﻿Due date;Frequency;Service;Price;Notes\n"
                + "01/11/2026;annually;\"Showmax; Premium\";R 1 299,50;\"card \"\"A\"\"\"\n");

        assertEquals(1, result.imported());
        Subscription sub = manager.getAll().get(0);
        assertEquals("Showmax; Premium", sub.getName());
        assertEquals(new BigDecimal("1299.50"), sub.getCost());
        assertEquals(BillingCycle.YEARLY, sub.getCycle());
        assertEquals(LocalDate.of(2026, 11, 1), sub.getNextPayment());
        assertEquals("card \"A\"", sub.getNote());
    }

    @Test
    public void badRowsAreSkippedWithAReasonAndTheRestImported() {
        CsvImporter.Result result = importText("Name,Cost,Billing Cycle,Next Payment\n"
                + ",10,Monthly,2026-10-01\n"
                + "A,abc,Monthly,2026-10-01\n"
                + "B,-5,Monthly,2026-10-01\n"
                + "C,10,Fortnightly,2026-10-01\n"
                + "D,10,Monthly,32/10/2026\n"
                + "\n"
                + "E,10,Monthly,2026-10-01\n");

        assertEquals(1, result.imported());
        assertEquals(List.of(
                "Row 2: it has no name.",
                "Row 3: the cost \"abc\" isn't an amount.",
                "Row 4: the cost \"-5\" is negative.",
                "Row 5: the billing cycle \"Fortnightly\" isn't weekly, monthly, quarterly or yearly.",
                "Row 6: the date \"32/10/2026\" isn't like 2026-10-01 or 01/10/2026."), result.problems());
        assertEquals("E", manager.getAll().get(0).getName());
    }

    @Test
    public void namesAlreadyInTheListOrRepeatedInTheFileAreSkipped() {
        manager.add("Netflix", BigDecimal.TEN, BillingCycle.MONTHLY, TODAY, "Streaming");

        CsvImporter.Result result = importText("Name,Cost\nnetflix,199\nSpotify,59.99\nSPOTIFY,59.99\n");

        assertEquals(1, result.imported());
        assertEquals(List.of("Row 2: \"netflix\" is already in your list.",
                "Row 4: \"SPOTIFY\" is already in your list."), result.problems());
    }

    @Test
    public void cancelledSubscriptionsDontBlockAnImport() {
        manager.add("Netflix", BigDecimal.TEN, BillingCycle.MONTHLY, TODAY, "Streaming").cancel(TODAY);

        assertEquals(1, importText("Name,Cost\nNetflix,199\n").imported());
    }

    @Test
    public void pastDatesAreRolledForward() {
        importText("Name,Cost,Next Payment\nGym,250,2026-07-15\n");

        assertEquals(LocalDate.of(2026, 10, 15), manager.getAll().get(0).getNextPayment());
    }

    @Test
    public void missingNameOrCostColumnIsReported() {
        CsvImporter.Result result = importText("Service,Category\nNetflix,Streaming\n");

        assertEquals(0, result.imported());
        assertEquals(List.of("The first row must name the columns, including Name and Cost."), result.problems());
    }

    @Test
    public void emptyFileIsReported() {
        assertEquals(List.of("The file is empty."), importText("").problems());
    }

    @Test
    public void amountsInCommonFormats() {
        assertEquals(new BigDecimal("199.00"), CsvImporter.parseAmount("199"));
        assertEquals(new BigDecimal("1299.00"), CsvImporter.parseAmount("R 1,299.00"));
        assertEquals(new BigDecimal("1299.50"), CsvImporter.parseAmount("$1 299.50"));
        assertEquals(new BigDecimal("99.90"), CsvImporter.parseAmount("99,9"));
        assertEquals(new BigDecimal("1299.00"), CsvImporter.parseAmount("1,299"));
    }

    @Test
    public void whenBothSeparatorsAppearTheLastOneIsTheDecimalPoint() {
        assertEquals(new BigDecimal("1299.50"), CsvImporter.parseAmount("1.299,50"));
        assertEquals(new BigDecimal("1299.50"), CsvImporter.parseAmount("€ 1.299,50"));
        assertEquals(new BigDecimal("1234567.89"), CsvImporter.parseAmount("1.234.567,89"));
        assertEquals(new BigDecimal("1234567.89"), CsvImporter.parseAmount("1,234,567.89"));
    }

    @Test
    public void datesInCommonFormats() {
        LocalDate expected = LocalDate.of(2026, 3, 1);
        assertEquals(expected, CsvImporter.parseDate("2026-03-01"));
        assertEquals(expected, CsvImporter.parseDate("2026/03/01"));
        assertEquals(expected, CsvImporter.parseDate("01/03/2026"));
        assertEquals(expected, CsvImporter.parseDate("1/3/2026"));
        try {
            CsvImporter.parseDate("March 1st");
            fail("expected an error");
        } catch (IllegalArgumentException expectedError) {
            // The date wasn't understood, as intended.
        }
    }

    @Test
    public void parsesQuotedCellsWithDelimitersAndLineBreaks() {
        List<List<String>> rows = CsvImporter.parse("a,\"b, c\",\"line 1\nline 2\"\r\n\"say \"\"hi\"\"\",,\n", ',');

        assertEquals(List.of(List.of("a", "b, c", "line 1\nline 2"), List.of("say \"hi\"", "", "")), rows);
    }

    @Test
    public void detectsTheDelimiterFromTheFirstLine() {
        assertEquals(',', CsvImporter.detectDelimiter("Name,Cost\n1;2;3"));
        assertEquals(';', CsvImporter.detectDelimiter("Name;Cost;Note\nA,B"));
        assertEquals('\t', CsvImporter.detectDelimiter("Name\tCost"));
    }
}
