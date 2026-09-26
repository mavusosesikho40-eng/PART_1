package subscriptiontracker.mobile.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Holds the list of subscriptions and answers questions about them. */
class SubscriptionManager {

    /** How the monthly total compares with the monthly budget. */
    enum class BudgetStatus { NO_BUDGET, UNDER, NEAR, OVER }

    /** A price change together with the subscription it belongs to. */
    data class PriceChangeEntry(val subscription: Subscription, val change: PriceChange)

    /** One payment on one day. A weekly subscription has several in a month. */
    data class Payment(val date: LocalDate, val subscription: Subscription)

    private val subscriptions = mutableListOf<Subscription>()
    private var nextId = 1

    /** The symbol amounts are shown with, e.g. "R" or "$"; empty for none. */
    var currencySymbol: String = ""

    /** The monthly budget in cents, or null if none is set. Zero or less removes it. */
    var monthlyBudget: Long? = null
        set(value) {
            field = if (value == null || value <= 0) null else value
        }

    val budgetStatus: BudgetStatus
        get() {
            val budget = monthlyBudget ?: return BudgetStatus.NO_BUDGET
            val monthly = monthlyTotal
            return when {
                monthly > budget -> BudgetStatus.OVER
                // At or above 90% of the budget counts as near it.
                monthly * 100 >= budget * NEAR_BUDGET_PERCENT -> BudgetStatus.NEAR
                else -> BudgetStatus.UNDER
            }
        }

    fun add(name: String, cost: Long, cycle: BillingCycle, nextPayment: LocalDate, category: String): Subscription {
        val sub = Subscription(nextId++, name, cost, cycle, nextPayment, category)
        subscriptions += sub
        return sub
    }

    /** Adds a subscription loaded from storage, keeping its existing id. */
    fun restore(sub: Subscription) {
        subscriptions += sub
        nextId = maxOf(nextId, sub.id + 1)
    }

    fun remove(id: Int): Boolean = subscriptions.removeAll { it.id == id }

    fun find(id: Int): Subscription? = subscriptions.firstOrNull { it.id == id }

    /** True when there are no active subscriptions (cancelled ones don't count). */
    val isEmpty: Boolean get() = active().isEmpty()

    private fun active() = subscriptions.filter { !it.isCancelled }

    private val byDateThenName = compareBy<Subscription> { it.nextPayment }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

    /** Active subscriptions, soonest payment first. Cancelled ones are left out. */
    val all: List<Subscription> get() = active().sortedWith(byDateThenName)

    /** Every subscription, active and cancelled, soonest payment first; used for saving. */
    val allIncludingCancelled: List<Subscription> get() = subscriptions.sortedWith(byDateThenName)

    /** Cancelled subscriptions, most recently cancelled first. */
    val cancelled: List<Subscription>
        get() = subscriptions.filter { it.isCancelled }
            .sortedWith(compareByDescending<Subscription> { it.cancelledOn }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** Total saved so far by all cancelled subscriptions. */
    fun savedSoFar(today: LocalDate): Long = subscriptions.sumOf { it.savedSoFar(today) }

    /** What the cancelled subscriptions would cost per month if they were still active. */
    val cancelledMonthlyTotal: Long get() = cancelled.sumOf { it.monthlyCost }

    /** Subscriptions due between today and today + days (inclusive). */
    fun upcoming(today: LocalDate, days: Int): List<Subscription> {
        val end = today.plus(days, DateTimeUnit.DAY)
        return all.filter { it.nextPayment >= today && it.nextPayment <= end }
    }

    /**
     * Every payment from today up to and including today + days, soonest
     * first, counting each repeat of a weekly or monthly subscription.
     */
    fun paymentsWithin(today: LocalDate, days: Int): List<Payment> {
        val end = today.plus(days, DateTimeUnit.DAY)
        val list = mutableListOf<Payment>()
        for (s in all) {
            var d = s.nextPayment
            while (d <= end) {
                if (d >= today) list += Payment(d, s)
                d = s.cycle.next(d, s.billingDay)
            }
        }
        return list.sortedWith(compareBy<Payment> { it.date }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.subscription.name })
    }

    /** Free trials, soonest ending first. */
    val freeTrials: List<Subscription> get() = all.filter { it.freeTrial }

    /** Free trials ending between today and today + days (inclusive), soonest first. */
    fun trialsEndingWithin(today: LocalDate, days: Int): List<Subscription> =
        upcoming(today, days).filter { it.freeTrial }

    /** Price changes of active subscriptions, newest first. */
    val priceChanges: List<PriceChangeEntry>
        get() = active().flatMap { s -> s.priceChanges.map { PriceChangeEntry(s, it) } }
            .sortedWith(compareByDescending<PriceChangeEntry> { it.change.date }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.subscription.name })

    /**
     * How much price changes since the given day have added to monthly
     * spending on active subscriptions (negative if prices went down).
     */
    fun monthlyPriceChangeSince(since: LocalDate): Long =
        active().sumOf { it.monthlyCost - it.monthlyCostOn(since) }

    val monthlyTotal: Long get() = active().sumOf { it.monthlyCost }
    val yearlyTotal: Long get() = active().sumOf { it.yearlyCost }

    /**
     * Monthly cost per category, biggest first (equal ones by name).
     * Categories differing only in case count as one.
     */
    val monthlyByCategory: List<Pair<String, Long>>
        get() {
            val names = mutableMapOf<String, String>()
            val totals = mutableMapOf<String, Long>()
            for (s in active()) {
                val key = s.category.lowercase()
                names.getOrPut(key) { s.category }
                totals[key] = (totals[key] ?: 0L) + s.monthlyCost
            }
            return totals.entries.map { names.getValue(it.key) to it.value }
                .sortedWith(compareByDescending<Pair<String, Long>> { it.second }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first })
        }

    /**
     * Rolls every overdue payment date forward to its next occurrence.
     *
     * @return how many subscriptions were updated
     */
    fun rollForwardAll(today: LocalDate): Int = active().count { it.rollForward(today) }

    companion object {
        /** Spending at or above this percentage of the budget counts as near it. */
        const val NEAR_BUDGET_PERCENT = 90
    }
}
