package subscriptiontracker.mobile.data

import kotlinx.datetime.LocalDate

/** A payment that was made: on [date], [amount] in the subscription's currency. */
data class Paid(
    val date: LocalDate,
    val amount: Long,
    /** False when it was recorded automatically at the list price; true once you've said what you paid. */
    val confirmed: Boolean,
)

/**
 * A single subscription the user pays for. Amounts are in cents, in the
 * subscription's [currency].
 */
class Subscription(
    val id: Int,
    var name: String,
    var cost: Long,
    var cycle: BillingCycle,
    nextPayment: LocalDate,
    var category: String,
) {
    /**
     * The next payment date. Setting a different date also makes its day the
     * day of the month the subscription is billed on; setting the same date
     * again keeps the billing day (so a payment moved to 28 Feb stays billed
     * on the 31st).
     */
    var nextPayment: LocalDate = nextPayment
        set(value) {
            if (value != field) billingDay = value.day
            field = value
        }

    /**
     * The day of the month payments are meant to fall on. It can be later
     * than the next payment's day when that month is too short.
     */
    var billingDay: Int = nextPayment.day
        private set

    /** Whether this is a free trial; the next payment date is then when the trial ends. */
    var freeTrial: Boolean = false

    /**
     * A permanent random name for this subscription, the same on every
     * device it's synced to (the [id] is only this device's number for it).
     */
    var uid: String = newUid()

    /** When it was last changed by you, in milliseconds since 1970; 0 if never. Used to merge synced copies. */
    var updated: Long = 0

    /** The currency it's billed in, e.g. "USD"; empty for your own currency. */
    var currency: String = ""
        set(value) {
            field = value.trim().uppercase()
        }

    val isForeign: Boolean get() = currency.isNotEmpty()

    /** A free-text note, e.g. which account or card it's on; empty if there is none. */
    var note: String = ""
        set(value) {
            field = value.trim()
        }

    /** The day it was cancelled, or null if it is active. */
    var cancelledOn: LocalDate? = null
        private set

    private val changes = mutableListOf<PriceChange>()
    private val paid = mutableListOf<Paid>()

    /** Payments made, oldest first. */
    val payments: List<Paid> get() = paid.sortedBy { it.date }

    /** Adds a payment loaded from storage. */
    fun restorePayment(payment: Paid) {
        paid.removeAll { it.date == payment.date }
        paid += payment
    }

    /**
     * Records that the next payment was made, for [amount] (which may differ
     * from the list price), and moves on to the payment after it. A free
     * trial becomes a paid subscription.
     */
    fun markPaid(amount: Long): Paid {
        val payment = Paid(nextPayment, amount, confirmed = true)
        restorePayment(payment)
        moveToNextPayment()
        freeTrial = false
        return payment
    }

    /** Changes what was paid on [date] to [amount], e.g. when it wasn't the list price. */
    fun correctPayment(date: LocalDate, amount: Long) {
        restorePayment(Paid(date, amount, confirmed = true))
    }

    /** An exact copy with another [id] (used when merging synced copies). */
    fun copy(id: Int): Subscription {
        val c = Subscription(id, name, cost, cycle, nextPayment, category)
        c.restoreBillingDay(billingDay)
        c.freeTrial = freeTrial
        c.note = note
        c.currency = currency
        c.uid = uid
        c.updated = updated
        cancelledOn?.let { c.cancel(it) }
        changes.forEach { c.restorePriceChange(it) }
        paid.forEach { c.restorePayment(it) }
        return c
    }

    private fun moveToNextPayment() {
        val day = billingDay
        nextPayment = cycle.next(nextPayment, day)
        billingDay = day
    }

    /** Recorded price changes, oldest first. */
    val priceChanges: List<PriceChange> get() = changes.toList()

    val hasNote: Boolean get() = note.isNotEmpty()
    val isCancelled: Boolean get() = cancelledOn != null
    val monthlyCost: Long get() = cycle.toMonthly(cost)
    val yearlyCost: Long get() = cycle.toYearly(cost)

    /** Sets the billing day loaded from storage (1 to 31). */
    fun restoreBillingDay(day: Int) {
        require(day in 1..31) { "billing day $day" }
        billingDay = day
    }

    /**
     * Changes the cost (and billing cycle, e.g. switching to a yearly plan)
     * and records the change. Nothing is recorded if neither changed.
     */
    fun changePrice(newCost: Long, newCycle: BillingCycle, today: LocalDate) {
        if (newCost == cost && newCycle == cycle) return
        changes += PriceChange(today, cost, newCost, cycle, newCycle)
        cost = newCost
        cycle = newCycle
    }

    /** Adds a price change loaded from storage; the current cost is left as it is. */
    fun restorePriceChange(change: PriceChange) {
        changes += change
    }

    /** What it cost per month on the given day, based on the recorded price changes. */
    fun monthlyCostOn(date: LocalDate): Long {
        for (change in changes) {
            if (change.date > date) return change.oldCycle.toMonthly(change.oldCost)
        }
        return monthlyCost
    }

    /**
     * Cancels it. The next payment date is kept as the first payment that
     * won't be made, which is where savings are counted from.
     */
    fun cancel(today: LocalDate) {
        cancelledOn = today
    }

    /**
     * Makes a cancelled subscription active again from today. The payments
     * skipped while it was cancelled aren't recorded as paid.
     */
    fun reactivate(today: LocalDate) {
        cancelledOn = null
        rollForward(today, record = false)
    }

    /**
     * How much has been saved by cancelling: the payments that would have
     * been made from the next payment date up to and including today.
     */
    fun savedSoFar(today: LocalDate): Long {
        if (!isCancelled) return 0
        var payments = 0L
        var d = nextPayment
        while (d <= today) {
            payments++
            d = cycle.next(d, billingDay)
        }
        return cost * payments
    }

    /**
     * Moves the next payment date forward past payments that have already
     * happened, so it points at today or later, recording each as paid at
     * the list price (unless [record] is false, or it's already recorded).
     * A free trial whose end date has passed becomes a normal paid
     * subscription. Cancelled subscriptions are left alone.
     *
     * @return true if the date changed
     */
    fun rollForward(today: LocalDate, record: Boolean = true): Boolean {
        if (isCancelled) return false
        var changed = false
        while (nextPayment < today) {
            if (record && paid.none { it.date == nextPayment }) paid += Paid(nextPayment, cost, confirmed = false)
            moveToNextPayment()
            changed = true
        }
        if (changed) freeTrial = false
        return changed
    }
}

/** 16 random hexadecimal characters. */
fun newUid(): String {
    val digits = "0123456789abcdef"
    return (1..16).map { digits[kotlin.random.Random.nextInt(16)] }.joinToString("")
}
