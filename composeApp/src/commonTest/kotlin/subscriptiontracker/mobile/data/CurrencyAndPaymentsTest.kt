package subscriptiontracker.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class CurrencyTest {

    private val today = LocalDate(2026, 9, 26)

    private fun manager() = SubscriptionManager().apply {
        currencySymbol = "R"
        setRate("usd", 18_250_000) // 1 USD = R 18.25
        add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "Streaming")
        add("ChatBot Plus", 2000, BillingCycle.MONTHLY, LocalDate(2026, 10, 5), "Software").currency = "usd"
        add("Domain", 1200, BillingCycle.YEARLY, LocalDate(2027, 3, 1), "Software").currency = "USD"
    }

    @Test
    fun totalsConvertOtherCurrencies() {
        val m = manager()
        // USD 20.00 a month is R 365.00; USD 12.00 a year is R 219.00, R 18.25 a month.
        assertEquals(19900 + 36500 + 1825, m.monthlyTotal)
        assertEquals(19900 * 12 + 36500 * 12 + 21900, m.yearlyTotal)
        assertEquals(listOf("Software" to 38325L, "Streaming" to 19900L), m.monthlyByCategory)
        assertEquals(36500, m.homeCost(m.all.first { it.name == "ChatBot Plus" }))
    }

    @Test
    fun aMissingRateCountsOneToOneAndIsReported() {
        val m = manager()
        m.all.first { it.name == "Domain" }.currency = "EUR"
        assertEquals(listOf("EUR"), m.currenciesWithoutRate)
        assertEquals(1200, m.homeYearly(m.all.first { it.name == "Domain" }))
        m.setRate("EUR", 20_000_000)
        assertTrue(m.currenciesWithoutRate.isEmpty())
        m.setRate("EUR", 0)
        assertNull(m.rates["EUR"])
    }

    @Test
    fun amountsShowTheirCurrency() {
        val f = Format("R")
        assertEquals("R 199.00", f.moneyIn("", 19900))
        assertEquals("USD 20.00", f.moneyIn("USD", 2000))
        assertEquals("USD 20.00 → USD 25.00 (+USD 5.00, +25%)",
            f.describe(PriceChange(today, 2000, 2500, BillingCycle.MONTHLY), "USD"))
        assertTrue(Format.isValidCurrencyCode("eur"))
        assertFalse(Format.isValidCurrencyCode("EURO"))
        assertEquals("18.25", Money.rateToString(18_250_000))
        assertEquals("19", Money.rateToString(19_000_000))
        assertEquals(18_250_000, Money.parseScaled("18.25", 6))
    }

    @Test
    fun theFormSetsTheCurrencyAndItsRate() {
        val m = SubscriptionManager()
        val form = SubscriptionForm(name = "ChatBot", cost = "20", date = LocalDate(2026, 10, 5), foreign = true, currency = "usd")
        assertEquals("Please enter the exchange rate, e.g. 18.25.", form.problem)
        assertEquals("Please enter the currency as a code like USD or EUR.", form.copy(currency = "dollars", rate = "18").problem)
        val sub = form.copy(rate = "18,25").add(m, today)
        assertEquals("USD", sub.currency)
        assertEquals(18_250_000, m.rates["USD"])
        assertEquals("18.25", SubscriptionForm.from(sub, m).rate)

        assertTrue(SubscriptionForm.from(sub, m).foreign)
        SubscriptionForm.from(sub, m).copy(foreign = false).applyTo(sub, today, m)
        assertEquals("", sub.currency)
    }

    @Test
    fun currencyAndRatesAreSavedAndCsvCarriesTheCurrency() {
        val m = manager()
        val fs = FakeFileSystem()
        fs.createDirectories("/d".toPath())
        val storage = Storage(fs, "/d/s.txt".toPath())
        storage.save(m)
        val text = fs.read("/d/s.txt".toPath()) { readUtf8() }
        assertTrue(text.contains("RATE\tUSD\t18.25\n"))
        assertTrue(text.contains("\tCUR=USD"))

        val copy = SubscriptionManager()
        assertEquals(0, storage.load(copy).skippedLines)
        assertEquals(m.monthlyTotal, copy.monthlyTotal)

        val imported = SubscriptionManager()
        CsvImporter.importText(CsvExporter.toCsv(m.all), imported, today)
        assertEquals(listOf("", "USD", "USD"), imported.all.map { it.currency })
        assertEquals(listOf("Row 2: the currency \"dollars\" isn't a code like USD."),
            CsvImporter.importText("Name,Cost,Currency\nX,1,dollars\n", SubscriptionManager(), today).problems)
    }
}

class PaymentsTest {

    private val today = LocalDate(2026, 9, 26)

    @Test
    fun passedPaymentsAreRecordedAtTheListPrice() {
        val m = SubscriptionManager()
        val s = m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 7, 1), "Streaming")

        s.rollForward(today)
        s.rollForward(today)

        assertEquals(listOf(
            Paid(LocalDate(2026, 7, 1), 19900, false),
            Paid(LocalDate(2026, 8, 1), 19900, false),
            Paid(LocalDate(2026, 9, 1), 19900, false),
        ), s.payments)
    }

    @Test
    fun markingAsPaidRecordsWhatWasPaidAndMovesOn() {
        val s = Subscription(1, "Disney+", 9900, BillingCycle.MONTHLY, LocalDate(2026, 1, 31), "Streaming")
        s.freeTrial = true
        s.rollForward(LocalDate(2026, 1, 20))

        val paid = s.markPaid(10500)

        assertEquals(Paid(LocalDate(2026, 1, 31), 10500, true), paid)
        assertEquals(LocalDate(2026, 2, 28), s.nextPayment)
        assertEquals(31, s.billingDay)
        assertFalse(s.freeTrial)
        // Already recorded, so rolling forward past 31 Jan doesn't add it again.
        s.rollForward(LocalDate(2026, 3, 1))
        assertEquals(listOf(10500L, 9900L), s.payments.map { it.amount })
        s.correctPayment(LocalDate(2026, 2, 28), 9000)
        assertEquals(Paid(LocalDate(2026, 2, 28), 9000, true), s.payments[1])
    }

    @Test
    fun restoringACancelledSubscriptionDoesNotRecordTheSkippedPayments() {
        val s = Subscription(1, "Gym", 45000, BillingCycle.MONTHLY, LocalDate(2026, 6, 1), "Health")
        s.cancel(LocalDate(2026, 5, 20))
        s.reactivate(today)
        assertEquals(LocalDate(2026, 10, 1), s.nextPayment)
        assertTrue(s.payments.isEmpty())
    }

    @Test
    fun spendingIsTotalledByMonthInYourCurrency() {
        val m = SubscriptionManager()
        m.setRate("USD", 18_000_000)
        val netflix = m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 7, 1), "Streaming")
        val bot = m.add("Bot", 1000, BillingCycle.MONTHLY, LocalDate(2026, 8, 15), "Software")
        bot.currency = "USD"
        m.rollForwardAll(today)
        netflix.cancel(today)

        assertEquals(listOf(
            SubscriptionManager.Month(LocalDate(2026, 7, 1), 19900),
            SubscriptionManager.Month(LocalDate(2026, 8, 1), 19900 + 18000),
            SubscriptionManager.Month(LocalDate(2026, 9, 1), 19900 + 18000),
        ), m.spentByMonth(today, 3))
        assertEquals(listOf("Bot", "Netflix", "Bot", "Netflix", "Netflix"), m.spent.map { it.subscription.name })
        assertEquals(18000, m.spent.first().home)
    }

    @Test
    fun paymentsAreSavedAndLoaded() {
        val m = SubscriptionManager()
        val s = m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 8, 1), "Streaming")
        m.rollForwardAll(today)
        s.markPaid(20500)
        val text = Storage.format(m)
        assertTrue(text.contains("\tPAID=2026-08-01:199.00:assumed\tPAID=2026-09-01:199.00:assumed\tPAID=2026-10-01:205.00"))

        val copy = SubscriptionManager()
        Storage.parse(text, copy)
        assertEquals(s.payments, copy.find(1)!!.payments)
    }
}
