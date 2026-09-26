package subscriptiontracker.gui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.SubscriptionManager;
import static org.junit.Assert.assertEquals;

public class UpcomingPanelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    @Test
    public void everyRepeatInTheRangeIsCountedSoonestFirst() {
        SubscriptionManager manager = new SubscriptionManager();
        manager.add("Coffee", new BigDecimal("40"), BillingCycle.WEEKLY, TODAY.plusDays(1), "Food");
        manager.add("Netflix", new BigDecimal("199"), BillingCycle.MONTHLY, TODAY.plusDays(5), "Streaming");
        manager.add("Office", new BigDecimal("1200"), BillingCycle.YEARLY, TODAY.plusDays(60), "Software");

        List<UpcomingPanel.Payment> payments = UpcomingPanel.paymentsWithin(manager, TODAY, 30);

        assertEquals(List.of(TODAY.plusDays(1), TODAY.plusDays(5), TODAY.plusDays(8), TODAY.plusDays(15),
                TODAY.plusDays(22), TODAY.plusDays(29)), payments.stream().map(UpcomingPanel.Payment::date).toList());
        assertEquals("Netflix", payments.get(1).subscription().getName());
    }

    @Test
    public void cancelledSubscriptionsHaveNoPayments() {
        SubscriptionManager manager = new SubscriptionManager();
        manager.add("Netflix", new BigDecimal("199"), BillingCycle.MONTHLY, TODAY.plusDays(5), "Streaming")
                .cancel(TODAY);

        assertEquals(0, UpcomingPanel.paymentsWithin(manager, TODAY, 30).size());
    }

    @Test
    public void thePaymentOnTheLastDayIsIncluded() {
        SubscriptionManager manager = new SubscriptionManager();
        manager.add("Netflix", new BigDecimal("199"), BillingCycle.MONTHLY, TODAY.plusDays(7), "Streaming");

        assertEquals(1, UpcomingPanel.paymentsWithin(manager, TODAY, 7).size());
        assertEquals(0, UpcomingPanel.paymentsWithin(manager, TODAY, 6).size());
    }
}
