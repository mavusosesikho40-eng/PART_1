package subscriptiontracker.gui;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.Test;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.PriceChange;
import subscriptiontracker.Subscription;
import subscriptiontracker.SubscriptionManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SubscriptionFormTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private static SubscriptionForm filled(String name, String cost, String date) {
        SubscriptionForm form = new SubscriptionForm();
        form.setName(name);
        form.setCost(cost);
        form.setDate(date);
        return form;
    }

    @Test
    public void aCompleteFormHasNoProblem() {
        assertNull(filled("Netflix", "199.00", "2026-10-01").problem());
    }

    @Test
    public void missingOrWrongFieldsAreExplained() {
        assertEquals("Please enter a name.", filled("  ", "199", "2026-10-01").problem());
        assertEquals("Please enter the cost as an amount, e.g. 99.99.", filled("Netflix", "abc", "2026-10-01").problem());
        assertEquals("Please enter the cost as an amount, e.g. 99.99.", filled("Netflix", "", "2026-10-01").problem());
        assertEquals("Please enter the date like 2026-10-01 or 01/10/2026.", filled("Netflix", "199", "soon").problem());
    }

    @Test
    public void datesCanBeTypedInSeveralWays() {
        assertEquals(LocalDate.of(2026, 10, 1), filled("a", "1", "2026-10-01").parsedDate());
        assertEquals(LocalDate.of(2026, 10, 1), filled("a", "1", " 01/10/2026 ").parsedDate());
        assertEquals(LocalDate.of(2026, 10, 1), filled("a", "1", "2026/10/1").parsedDate());
    }

    @Test
    public void addingKeepsEveryFieldOnOneLine() {
        SubscriptionManager manager = new SubscriptionManager();
        SubscriptionForm form = filled("Net\tflix", "199", "2026-10-01");
        form.setNote("shared\nwith family");
        form.setFreeTrial(true);
        form.setCycle(BillingCycle.QUARTERLY);

        Subscription sub = form.add(manager, TODAY);

        assertEquals("Net flix", sub.getName());
        assertEquals("shared with family", sub.getNote());
        assertEquals("Other", sub.getCategory());
        assertTrue(sub.isFreeTrial());
        assertEquals(BillingCycle.QUARTERLY, sub.getCycle());
        assertEquals(1, manager.getAll().size());
    }

    @Test
    public void aDateInThePastMovesToTheNextPayment() {
        SubscriptionManager manager = new SubscriptionManager();

        Subscription sub = filled("Netflix", "199", "2026-09-01").add(manager, TODAY);

        assertEquals(LocalDate.of(2026, 10, 1), sub.getNextPayment());
    }

    @Test
    public void aNewPriceIsRecordedAsAPriceChange() {
        Subscription sub = new Subscription(1, "Netflix", new BigDecimal("169.00"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming");
        SubscriptionForm form = SubscriptionForm.from(sub);
        form.setCost("199.00");

        assertNotNull(form.pendingPriceChange(sub, TODAY));
        PriceChange recorded = form.applyTo(sub, TODAY);

        assertNotNull(recorded);
        assertEquals(new BigDecimal("169.00"), recorded.oldCost());
        assertEquals(new BigDecimal("199.00"), recorded.newCost());
        assertEquals(new BigDecimal("199.00"), sub.getCost());
        assertEquals(1, sub.getPriceChanges().size());
    }

    @Test
    public void correctingAMistakeKeepsNoPriceHistory() {
        Subscription sub = new Subscription(1, "Netflix", new BigDecimal("1990.00"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming");
        SubscriptionForm form = SubscriptionForm.from(sub);
        form.setCost("199.00");
        form.setRecordPriceChange(false);

        assertNull(form.applyTo(sub, TODAY));
        assertEquals(new BigDecimal("199.00"), sub.getCost());
        assertTrue(sub.getPriceChanges().isEmpty());
    }

    @Test
    public void anUnchangedFormChangesNothing() {
        Subscription sub = new Subscription(1, "Netflix", new BigDecimal("199.00"), BillingCycle.MONTHLY,
                LocalDate.of(2026, 10, 1), "Streaming");
        sub.setNote("family");
        SubscriptionForm form = SubscriptionForm.from(sub);

        assertNull(form.pendingPriceChange(sub, TODAY));
        assertNull(form.applyTo(sub, TODAY));
        assertEquals("Netflix", sub.getName());
        assertEquals("Streaming", sub.getCategory());
        assertEquals("family", sub.getNote());
        assertEquals(LocalDate.of(2026, 10, 1), sub.getNextPayment());
        assertTrue(sub.getPriceChanges().isEmpty());
    }

    @Test
    public void billingDescriptionSaysWhenPaymentsFall() {
        SubscriptionForm form = filled("a", "1", "2026-10-01");
        assertTrue(form.billingDescription(TODAY).startsWith("Billed on the 1st of each month · "));

        form.setCycle(BillingCycle.YEARLY);
        assertTrue(form.billingDescription(TODAY).startsWith("Billed every year on 1 Oct"));

        form.setCycle(BillingCycle.MONTHLY);
        form.setDate("2026-09-01");
        assertEquals("Billed on the 1st of each month · that date has passed, so the next one is 1 Oct 2026",
                form.billingDescription(TODAY));

        form.setDate("not a date");
        assertNull(form.billingDescription(TODAY));
    }

    @Test
    public void ordinalsReadNaturally() {
        assertEquals("1st", SubscriptionForm.ordinal(1));
        assertEquals("2nd", SubscriptionForm.ordinal(2));
        assertEquals("3rd", SubscriptionForm.ordinal(3));
        assertEquals("11th", SubscriptionForm.ordinal(11));
        assertEquals("12th", SubscriptionForm.ordinal(12));
        assertEquals("13th", SubscriptionForm.ordinal(13));
        assertEquals("21st", SubscriptionForm.ordinal(21));
        assertEquals("22nd", SubscriptionForm.ordinal(22));
        assertEquals("31st", SubscriptionForm.ordinal(31));
    }

    @Test
    public void negativeCostIsNotAnAmount() {
        assertFalse(filled("a", "-5", "2026-10-01").problem() == null);
    }
}
