package subscriptiontracker;

import java.math.BigDecimal;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DisplayTest {

    private Locale originalLocale;

    @Before
    public void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void tearDown() {
        // The currency symbol is shared by the whole app, so put it back.
        Display.setCurrency("");
        Locale.setDefault(originalLocale);
    }

    @Test
    public void moneyWithoutACurrencySymbol() {
        assertEquals("1,234.50", Display.money(new BigDecimal("1234.5")));
        assertEquals("-30.00", Display.money(new BigDecimal("-30")));
    }

    @Test
    public void lettersGetASpaceAndSignsDoNot() {
        Display.setCurrency("R");
        assertEquals("R 1,234.50", Display.money(new BigDecimal("1234.5")));

        Display.setCurrency("USD");
        assertEquals("USD 5.00", Display.money(new BigDecimal("5")));

        Display.setCurrency("$");
        assertEquals("$1,234.50", Display.money(new BigDecimal("1234.5")));

        Display.setCurrency("US$");
        assertEquals("US$5.00", Display.money(new BigDecimal("5")));
    }

    @Test
    public void minusSignGoesBeforeTheSymbol() {
        Display.setCurrency("R");
        assertEquals("-R 30.00", Display.money(new BigDecimal("-30")));
        assertEquals("+R 30.00", Display.signedMoney(new BigDecimal("30")));
    }

    @Test
    public void priceChangeDescriptionUsesTheSymbol() {
        Display.setCurrency("R");
        PriceChange change = new PriceChange(java.time.LocalDate.of(2026, 9, 1),
                new BigDecimal("169.00"), new BigDecimal("199.00"), BillingCycle.MONTHLY);

        assertEquals("R 169.00 -> R 199.00 (+R 30.00, +18%)", Display.describe(change));
    }

    @Test
    public void priceChangeWithANewBillingCycleIsDescribedPerMonth() {
        PriceChange change = new PriceChange(java.time.LocalDate.of(2026, 9, 1),
                new BigDecimal("199.00"), new BigDecimal("2000.00"), BillingCycle.MONTHLY, BillingCycle.YEARLY);

        assertEquals("199.00 a month -> 2,000.00 a year (-32.33 a month, -16%)", Display.describe(change));
    }
}
