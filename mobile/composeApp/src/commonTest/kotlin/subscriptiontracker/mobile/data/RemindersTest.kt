package subscriptiontracker.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class RemindersTest {

    private val today = LocalDate(2026, 9, 26)

    @Test
    fun paymentsAreRemindedTheDayBefore() {
        val m = SubscriptionManager()
        m.currencySymbol = "R"
        m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "Streaming")

        val reminders = Reminders.upcoming(m, today, 30)

        assertEquals(listOf(Reminder("pay-1-2026-10-01", LocalDate(2026, 9, 30), "Netflix is due tomorrow",
            "R 199.00 on 1 Oct 2026.")), reminders)
    }

    @Test
    fun trialsAreRemindedTwoDaysAndOneDayBeforeInsteadOfThePayment() {
        val m = SubscriptionManager()
        val trial = m.add("Disney+", 9900, BillingCycle.MONTHLY, LocalDate(2026, 10, 5), "Streaming")
        trial.freeTrial = true

        val reminders = Reminders.upcoming(m, today, 30)

        assertEquals(listOf(
            LocalDate(2026, 10, 3) to "Disney+ free trial ends in 2 days",
            LocalDate(2026, 10, 4) to "Disney+ free trial ends tomorrow",
        ), reminders.map { it.on to it.title })
        assertEquals("You'll be charged 99.00 on 5 Oct 2026 unless you cancel.", reminders[0].text)
    }

    @Test
    fun onlyRemindersFromTodayToTheEndOfTheRangeAreGiven() {
        val m = SubscriptionManager()
        m.add("Due today", 100, BillingCycle.MONTHLY, today, "X")
        m.add("Due tomorrow", 100, BillingCycle.MONTHLY, LocalDate(2026, 9, 27), "X")
        m.add("Weekly", 100, BillingCycle.WEEKLY, LocalDate(2026, 9, 28), "X")
        m.add("Cancelled", 100, BillingCycle.MONTHLY, LocalDate(2026, 9, 28), "X").cancel(today)

        val reminders = Reminders.upcoming(m, today, 7)

        // Today's payment was reminded yesterday; the weekly one's 5 Oct reminder (on 4 Oct) is
        // after 3 Oct, the last day in range; cancelled ones get none.
        assertEquals(listOf("pay-2-2026-09-27", "pay-3-2026-09-28"), reminders.map { it.id })
        assertTrue(reminders.all { it.on >= today })
    }
}

class SummaryAndBackupTest {

    private val today = LocalDate(2026, 9, 26)

    @Test
    fun summaryShowsTheMonthAndTheNextPayment() {
        val m = SubscriptionManager()
        assertEquals(Summary("0.00", "No subscriptions yet"), Summary.of(m, today))
        m.currencySymbol = "R"
        m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 10, 1), "Streaming")
        m.add("Spotify", 7999, BillingCycle.MONTHLY, LocalDate(2026, 9, 28), "Music")
        assertEquals(Summary("R 278.99", "Next: Spotify · R 79.99 · in 2 days"), Summary.of(m, today))
    }

    @Test
    fun aBackupRestoresEverythingAndMovesOverdueDatesOn() {
        val text = "BUDGET\t500.00\nCURRENCY\tR\n" +
            "1\tNetflix\t199.00\tMONTHLY\t2026-09-01\tStreaming\tPRICE=2026-03-01:169.00:199.00\n" +
            "2\tShowmax\t99.00\tMONTHLY\t2026-06-12\tEntertainment\tCANCELLED=2026-06-01\n" +
            "not a subscription\n"

        val backup = Backup.read(text, today)

        assertEquals(1, backup.skippedLines)
        assertEquals(50000, backup.manager.monthlyBudget)
        assertEquals("R", backup.manager.currencySymbol)
        assertEquals(LocalDate(2026, 10, 1), backup.manager.find(1)!!.nextPayment)
        assertEquals(1, backup.manager.find(1)!!.priceChanges.size)
        assertEquals(LocalDate(2026, 6, 12), backup.manager.find(2)!!.nextPayment)
        assertTrue(!backup.isEmpty)
        assertTrue(Backup.read("hello\n", today).isEmpty)
    }
}
