package subscriptiontracker.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class MoneyTest {

    @Test
    fun formatsWithThousandsAndTwoDecimals() {
        assertEquals("1,234,567.50", Money.plain(123456750))
        assertEquals("0.05", Money.plain(5))
        assertEquals("-30.00", Money.plain(-3000))
        assertEquals("1234.50", Money.toPlainString(123450))
    }

    @Test
    fun readsPlainDecimalsRoundingHalfUp() {
        assertEquals(19900, Money.parsePlain("199"))
        assertEquals(19950, Money.parsePlain("199.5"))
        assertEquals(123457, Money.parsePlain("1234.565"))
        assertEquals(123456, Money.parsePlain("1234.564"))
        assertEquals(50, Money.parsePlain(".5"))
        assertEquals(-3000, Money.parsePlain("-30.00"))
    }

    @Test
    fun divisionRoundsHalfAwayFromZero() {
        assertEquals(3, Money.divideRounded(5, 2))
        assertEquals(-3, Money.divideRounded(-5, 2))
        assertEquals(2, Money.divideRounded(9, 4))
    }
}

class BillingCycleTest {

    @Test
    fun costsConvertLikeTheDesktopApp() {
        assertEquals(240000, BillingCycle.YEARLY.toYearly(240000))
        assertEquals(20000, BillingCycle.YEARLY.toMonthly(240000))
        // 40.00 a week is 2,080.00 a year, 173.33 a month.
        assertEquals(17333, BillingCycle.WEEKLY.toMonthly(4000))
        // 99.99 a year is 8.3325 a month, rounded half up to 8.33.
        assertEquals(833, BillingCycle.YEARLY.toMonthly(9999))
        assertEquals(3333, BillingCycle.QUARTERLY.toMonthly(10000))
    }

    @Test
    fun nextKeepsTheBillingDayThroughShortMonths() {
        val jan31 = LocalDate(2026, 1, 31)
        val feb = BillingCycle.MONTHLY.next(jan31, 31)
        assertEquals(LocalDate(2026, 2, 28), feb)
        assertEquals(LocalDate(2026, 3, 31), BillingCycle.MONTHLY.next(feb, 31))
        assertEquals(LocalDate(2026, 10, 8), BillingCycle.WEEKLY.next(LocalDate(2026, 10, 1)))
        assertEquals(LocalDate(2027, 2, 28), BillingCycle.YEARLY.next(LocalDate(2026, 2, 28)))
        assertEquals(LocalDate(2028, 2, 29), BillingCycle.YEARLY.next(LocalDate(2027, 2, 28), 29))
    }
}

class SubscriptionTest {

    private val today = LocalDate(2026, 9, 26)

    private fun sub(cost: Long = 19900, cycle: BillingCycle = BillingCycle.MONTHLY, date: LocalDate = today) =
        Subscription(1, "Netflix", cost, cycle, date, "Streaming")

    @Test
    fun rollForwardSkipsPastPaymentsAndEndsTrials() {
        val s = sub(date = LocalDate(2026, 7, 1))
        s.freeTrial = true
        assertTrue(s.rollForward(today))
        assertEquals(LocalDate(2026, 10, 1), s.nextPayment)
        assertFalse(s.freeTrial)
        assertFalse(s.rollForward(today))
    }

    @Test
    fun paymentDueTodayIsLeftAlone() {
        val s = sub()
        s.freeTrial = true
        assertFalse(s.rollForward(today))
        assertTrue(s.freeTrial)
    }

    @Test
    fun rollingForwardKeepsTheBillingDay() {
        val s = sub(date = LocalDate(2026, 1, 31))
        s.rollForward(LocalDate(2026, 2, 10))
        assertEquals(LocalDate(2026, 2, 28), s.nextPayment)
        assertEquals(31, s.billingDay)
        s.rollForward(LocalDate(2026, 3, 1))
        assertEquals(LocalDate(2026, 3, 31), s.nextPayment)
    }

    @Test
    fun settingANewDateChangesTheBillingDayButTheSameDateDoesNot() {
        val s = sub(date = LocalDate(2026, 2, 28))
        s.restoreBillingDay(31)
        s.nextPayment = LocalDate(2026, 2, 28)
        assertEquals(31, s.billingDay)
        s.nextPayment = LocalDate(2026, 3, 15)
        assertEquals(15, s.billingDay)
    }

    @Test
    fun cancellingCountsSavingsAndReactivatingMovesTheDateOn() {
        val s = sub(date = LocalDate(2026, 7, 1))
        s.cancel(LocalDate(2026, 6, 20))
        assertFalse(s.rollForward(today))
        // 1 Jul, 1 Aug and 1 Sep weren't paid.
        assertEquals(3 * 19900, s.savedSoFar(today))
        s.reactivate(today)
        assertNull(s.cancelledOn)
        assertEquals(LocalDate(2026, 10, 1), s.nextPayment)
        assertEquals(0, s.savedSoFar(today))
    }

    @Test
    fun priceChangesAreRecordedWithTheirPercent() {
        val s = sub(cost = 16900)
        s.changePrice(16900, BillingCycle.MONTHLY, today)
        assertTrue(s.priceChanges.isEmpty())
        s.changePrice(19900, BillingCycle.MONTHLY, LocalDate(2026, 3, 1))
        val change = s.priceChanges.single()
        assertEquals(3000, change.difference)
        assertEquals(18, change.percent)
        assertEquals(16900, s.monthlyCostOn(LocalDate(2026, 2, 1)))
        assertEquals(19900, s.monthlyCostOn(LocalDate(2026, 3, 1)))
    }

    @Test
    fun switchingToAYearlyPlanIsComparedPerMonth() {
        val change = PriceChange(today, 19900, 200000, BillingCycle.MONTHLY, BillingCycle.YEARLY)
        // 2,000.00 a year is 166.67 a month: 32.33 less, -16%.
        assertEquals(-3233, change.monthlyDifference)
        assertEquals(-16, change.percent)
        assertNull(PriceChange(today, 0, 100, BillingCycle.MONTHLY).percent)
    }
}

class SubscriptionManagerTest {

    private val today = LocalDate(2026, 9, 26)

    private fun manager() = SubscriptionManager().apply {
        add("Spotify", 7999, BillingCycle.MONTHLY, LocalDate(2026, 9, 28), "Music")
        add("netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "Streaming")
        add("Office", 120000, BillingCycle.YEARLY, LocalDate(2027, 1, 15), "Software")
        add("Coffee", 4000, BillingCycle.WEEKLY, LocalDate(2026, 9, 27), "streaming")
    }

    @Test
    fun listsAreSoonestFirstAndTotalsUseMonthlyEquivalents() {
        val m = manager()
        assertEquals(listOf("Coffee", "Spotify", "netflix", "Office"), m.all.map { it.name })
        // 79.99 + 199.00 + 100.00 + 173.33
        assertEquals(55232, m.monthlyTotal)
        assertEquals(7999 * 12 + 19900 * 12 + 120000 + 4000 * 52, m.yearlyTotal)
    }

    @Test
    fun categoriesDifferingOnlyInCaseAreOne() {
        val m = manager()
        assertEquals(listOf("Streaming" to 37233L, "Software" to 10000L, "Music" to 7999L), m.monthlyByCategory)
    }

    @Test
    fun budgetIsNearAtNinetyPercentAndOverAboveIt() {
        val m = manager()
        assertEquals(SubscriptionManager.BudgetStatus.NO_BUDGET, m.budgetStatus)
        m.monthlyBudget = 100000
        assertEquals(SubscriptionManager.BudgetStatus.UNDER, m.budgetStatus)
        m.monthlyBudget = 61369 // 55232 is 89.9999% of it
        assertEquals(SubscriptionManager.BudgetStatus.UNDER, m.budgetStatus)
        m.monthlyBudget = 61368 // 55232 is 90.0013% of it
        assertEquals(SubscriptionManager.BudgetStatus.NEAR, m.budgetStatus)
        m.monthlyBudget = 55231
        assertEquals(SubscriptionManager.BudgetStatus.OVER, m.budgetStatus)
        m.monthlyBudget = 0
        assertNull(m.monthlyBudget)
    }

    @Test
    fun upcomingIncludesBothEndsAndPaymentsCountEveryRepeat() {
        val m = manager()
        assertEquals(listOf("Coffee", "Spotify", "netflix"), m.upcoming(today, 5).map { it.name })
        val payments = m.paymentsWithin(today, 15)
        assertEquals(listOf(
            LocalDate(2026, 9, 27), LocalDate(2026, 9, 28), LocalDate(2026, 10, 1),
            LocalDate(2026, 10, 4), LocalDate(2026, 10, 11),
        ), payments.map { it.date })
    }

    @Test
    fun cancelledSubscriptionsAreLeftOutButKeptForSavings() {
        val m = manager()
        val netflix = m.all.first { it.name == "netflix" }
        netflix.cancel(today)
        assertEquals(3, m.all.size)
        assertEquals(listOf(netflix), m.cancelled)
        assertEquals(19900, m.cancelledMonthlyTotal)
        assertEquals(4, m.allIncludingCancelled.size)
        assertTrue(m.paymentsWithin(today, 30).none { it.subscription === netflix })
    }

    @Test
    fun trialsAndPriceChanges() {
        val m = manager()
        m.all.first { it.name == "Spotify" }.freeTrial = true
        assertEquals(listOf("Spotify"), m.trialsEndingWithin(today, 7).map { it.name })
        assertTrue(m.trialsEndingWithin(today, 1).isEmpty())

        m.all.first { it.name == "netflix" }.changePrice(22900, BillingCycle.MONTHLY, LocalDate(2026, 5, 1))
        assertEquals(1, m.priceChanges.size)
        assertEquals(3000, m.monthlyPriceChangeSince(LocalDate(2025, 9, 26)))
        assertEquals(0, m.monthlyPriceChangeSince(LocalDate(2026, 6, 1)))
    }

    @Test
    fun idsKeepIncreasingAfterRestoreAndRemove() {
        val m = SubscriptionManager()
        m.restore(Subscription(7, "A", 100, BillingCycle.MONTHLY, today, "X"))
        assertEquals(8, m.add("B", 100, BillingCycle.MONTHLY, today, "X").id)
        assertTrue(m.remove(8))
        assertEquals(9, m.add("C", 100, BillingCycle.MONTHLY, today, "X").id)
    }
}

class DatesTest {

    @Test
    fun pickerMillisRoundTrip() {
        val date = LocalDate(2026, 10, 1)
        assertEquals(1790812800000, Dates.toPickerMillis(date))
        assertEquals(date, Dates.fromPickerMillis(Dates.toPickerMillis(date)))
        assertEquals(date, Dates.fromPickerMillis(Dates.toPickerMillis(date) + 5 * 60 * 60 * 1000))
        assertEquals(LocalDate(1969, 12, 31), Dates.fromPickerMillis(-1))
    }
}

class ListViewTest {

    private val date = LocalDate(2026, 10, 1)
    private val netflix = Subscription(1, "Netflix", 19900, BillingCycle.MONTHLY, date, "Streaming")
    private val office = Subscription(2, "office", 120000, BillingCycle.YEARLY, LocalDate(2027, 1, 15), "Software")
    private val coffee = Subscription(3, "Coffee", 4000, BillingCycle.WEEKLY, LocalDate(2026, 9, 27), "Food")

    @Test
    fun searchMatchesNameOrCategoryIgnoringCase() {
        assertTrue(ListView.matches(netflix, "", null))
        assertTrue(ListView.matches(netflix, " NETF ", null))
        assertTrue(ListView.matches(netflix, "stream", null))
        assertFalse(ListView.matches(netflix, "soft", null))
        assertTrue(ListView.matches(office, "", BillingCycle.YEARLY))
        assertFalse(ListView.matches(netflix, "", BillingCycle.YEARLY))
    }

    @Test
    fun sortsEachWay() {
        val all = listOf(netflix, office, coffee)
        assertEquals(listOf(coffee, netflix, office), ListView.sort(all, ListView.Sort.NEXT_PAYMENT))
        assertEquals(listOf(coffee, netflix, office), ListView.sort(all, ListView.Sort.NAME))
        // Per month: coffee 173.33, netflix 199.00, office 100.00.
        assertEquals(listOf(netflix, coffee, office), ListView.sort(all, ListView.Sort.COST))
        assertEquals(listOf(coffee, office, netflix), ListView.sort(all, ListView.Sort.CATEGORY))
    }
}

class ChartScaleTest {

    @Test
    fun ticksAreRoundAndCoverTheMaximum() {
        assertEquals(listOf(0L, 50000, 100000, 150000, 200000), ChartScale.ticks(160000))
        assertEquals(listOf(0L, 20000, 40000, 60000), ChartScale.ticks(44998))
        assertEquals(listOf(0L, 25000, 50000, 75000, 100000), ChartScale.ticks(90000))
        assertEquals(listOf(0L, 10000), ChartScale.ticks(0))
        assertTrue(ChartScale.ticks(1).last() >= 1)
    }
}
