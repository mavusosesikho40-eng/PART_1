package subscriptiontracker.gui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.Subscription;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SubscriptionTableModelTest {

    private final Subscription netflix = new Subscription(1, "Netflix", new BigDecimal("199.00"),
            BillingCycle.MONTHLY, LocalDate.of(2026, 10, 1), "Streaming");
    private final Subscription office = new Subscription(2, "Microsoft 365", new BigDecimal("1200.00"),
            BillingCycle.YEARLY, LocalDate.of(2027, 1, 15), "Software");

    @Test
    public void emptySearchAndNoCycleShowEverything() {
        assertTrue(SubscriptionTableModel.matches(netflix, "", null));
        assertTrue(SubscriptionTableModel.matches(netflix, null, null));
        assertTrue(SubscriptionTableModel.matches(netflix, "   ", null));
    }

    @Test
    public void searchMatchesNameOrCategoryIgnoringCase() {
        assertTrue(SubscriptionTableModel.matches(netflix, "NETF", null));
        assertTrue(SubscriptionTableModel.matches(netflix, "stream", null));
        assertTrue(SubscriptionTableModel.matches(netflix, " flix ", null));
        assertFalse(SubscriptionTableModel.matches(netflix, "software", null));
    }

    @Test
    public void cycleFilterKeepsOnlyThatCycle() {
        assertTrue(SubscriptionTableModel.matches(office, "", BillingCycle.YEARLY));
        assertFalse(SubscriptionTableModel.matches(netflix, "", BillingCycle.YEARLY));
        assertFalse(SubscriptionTableModel.matches(office, "netflix", BillingCycle.YEARLY));
    }

    @Test
    public void columnsHoldTheValuesSoSortingIsByValue() {
        SubscriptionTableModel model = new SubscriptionTableModel();
        model.setRows(List.of(netflix, office));

        assertEquals(2, model.getRowCount());
        assertSame(netflix, model.getValueAt(0, SubscriptionTableModel.NAME));
        assertEquals("Software", model.getValueAt(1, SubscriptionTableModel.CATEGORY));
        assertEquals(new BigDecimal("1200.00"), model.getValueAt(1, SubscriptionTableModel.COST));
        assertEquals(BillingCycle.YEARLY, model.getValueAt(1, SubscriptionTableModel.CYCLE));
        assertEquals(new BigDecimal("100.00"), model.getValueAt(1, SubscriptionTableModel.PER_MONTH));
        assertEquals(LocalDate.of(2027, 1, 15), model.getValueAt(1, SubscriptionTableModel.NEXT_PAYMENT));
        assertEquals(BigDecimal.class, model.getColumnClass(SubscriptionTableModel.COST));
    }
}
