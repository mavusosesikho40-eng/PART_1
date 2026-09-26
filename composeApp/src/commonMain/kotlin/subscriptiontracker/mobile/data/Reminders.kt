package subscriptiontracker.mobile.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** A notification to show on a given day, e.g. "Netflix is due tomorrow". */
data class Reminder(val id: String, val on: LocalDate, val title: String, val text: String)

/**
 * Which reminders to show, and when: the day before each payment, and two
 * days and one day before a free trial ends (instead of the usual payment
 * reminder, since that first charge is the one to avoid). Android checks
 * once a day for today's; iPhone schedules them all ahead.
 */
object Reminders {

    fun upcoming(manager: SubscriptionManager, today: LocalDate, days: Int = 30): List<Reminder> {
        val format = Format(manager.currencySymbol)
        val list = mutableListOf<Reminder>()
        for (p in manager.paymentsWithin(today, days + 2)) {
            val s = p.subscription
            if (s.freeTrial && p.date == s.nextPayment) {
                for (before in listOf(2, 1)) {
                    val on = p.date.minus(before, DateTimeUnit.DAY)
                    list += Reminder(
                        id = "trial-${s.id}-${p.date}-$before",
                        on = on,
                        title = "${s.name} free trial ends ${if (before == 1) "tomorrow" else "in 2 days"}",
                        text = "You'll be charged ${format.moneyIn(s.currency, s.cost)} on ${Format.date(p.date)} unless you cancel.",
                    )
                }
            } else {
                list += Reminder(
                    id = "pay-${s.id}-${p.date}",
                    on = p.date.minus(1, DateTimeUnit.DAY),
                    title = "${s.name} is due tomorrow",
                    text = "${format.moneyIn(s.currency, s.cost)} on ${Format.date(p.date)}.",
                )
            }
        }
        val last = today.plusDays(days)
        return list.filter { it.on >= today && it.on <= last }.sortedBy { it.on }
    }

    private fun LocalDate.plusDays(days: Int): LocalDate = minus(-days, DateTimeUnit.DAY)
}

/** The two lines the home-screen widget shows. */
data class Summary(val monthly: String, val next: String) {
    companion object {
        fun of(manager: SubscriptionManager, today: LocalDate): Summary {
            val format = Format(manager.currencySymbol)
            val next = manager.all.firstOrNull()
            return Summary(
                monthly = format.money(manager.monthlyTotal),
                next = if (next == null) "No subscriptions yet" else
                    "Next: ${next.name} · ${format.moneyIn(next.currency, next.cost)} · ${Format.dueIn(next.nextPayment, today)}",
            )
        }
    }
}

/** A backup file (the data file itself) read back in. */
data class Backup(val manager: SubscriptionManager, val skippedLines: Int) {

    val isEmpty: Boolean get() = manager.allIncludingCancelled.isEmpty() && manager.monthlyBudget == null

    companion object {
        /**
         * Reads a backup: the phone's own, or the desktop app's
         * subscriptions.txt. Overdue dates are moved forward to [today].
         */
        fun read(text: String, today: LocalDate): Backup {
            val manager = SubscriptionManager()
            val skipped = Storage.parse(text, manager)
            manager.rollForwardAll(today)
            return Backup(manager, skipped)
        }
    }
}
