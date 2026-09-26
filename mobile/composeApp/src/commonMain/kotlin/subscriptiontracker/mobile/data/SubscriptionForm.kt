package subscriptiontracker.mobile.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * What's typed into the add/edit screen: checked and applied here, with no
 * screen involved, so it can be tested on its own.
 */
data class SubscriptionForm(
    val name: String = "",
    val cost: String = "",
    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val freeTrial: Boolean = false,
    val date: LocalDate? = null,
    val category: String = "",
    val note: String = "",
    /** Whether a changed cost is recorded as a price change (true) or fixes a mistake (false). */
    val recordPriceChange: Boolean = true,
    /** Whether it's billed in another currency. */
    val foreign: Boolean = false,
    /** That currency's code, e.g. "USD". */
    val currency: String = "",
    /** For another currency: how much one unit is in your currency, e.g. "18.25". */
    val rate: String = "",
) {
    val isForeign: Boolean get() = foreign

    /** The exchange rate in millionths, or null if it isn't a positive number. */
    val parsedRate: Long?
        get() = try {
            Money.parseScaled(rate.replace(',', '.'), 6).takeIf { it > 0 }
        } catch (e: NumberFormatException) {
            null
        }

    /** The cost in cents, or null if it isn't an amount (or is negative). */
    val parsedCost: Long?
        get() = if (cost.isBlank()) null else try {
            CsvImporter.parseAmount(cost)
        } catch (e: IllegalArgumentException) {
            null
        }

    /** What stops the form being saved, as a sentence, or null if it can be saved. */
    val problem: String?
        get() = when {
            oneLine(name).isEmpty() -> "Please enter a name."
            parsedCost == null -> "Please enter the cost as an amount, e.g. 99.99."
            date == null -> "Please choose the date."
            isForeign && !Format.isValidCurrencyCode(currency) -> "Please enter the currency as a code like USD or EUR."
            isForeign && parsedRate == null -> "Please enter the exchange rate, e.g. 18.25."
            else -> null
        }

    /**
     * The price change saving would record for this subscription, or null
     * if the cost is unchanged (or not yet a valid amount).
     */
    fun pendingPriceChange(s: Subscription, today: LocalDate): PriceChange? {
        val newCost = parsedCost ?: return null
        if (newCost == s.cost) return null
        return PriceChange(today, s.cost, newCost, s.cycle, cycle)
    }

    /**
     * When payments fall, e.g. "Billed on the 1st of each month · in 5 days",
     * or null without a date. A date in the past says where it will move to.
     */
    fun billingDescription(today: LocalDate): String? {
        val first = date ?: return null
        val whenText = when (cycle) {
            BillingCycle.WEEKLY -> "Billed every " + dayName(first.dayOfWeek)
            BillingCycle.MONTHLY -> "Billed on the " + Format.ordinal(first.day) + " of each month"
            BillingCycle.QUARTERLY -> "Billed every 3 months on the " + Format.ordinal(first.day)
            BillingCycle.YEARLY -> "Billed every year on " + Format.shortDate(first)
        }
        val preview = Subscription(0, "", 100, cycle, first, "")
        preview.rollForward(today)
        val next = preview.nextPayment
        return if (next == first) {
            whenText + " · " + Format.dueIn(first, today)
        } else {
            whenText + " · that date has passed, so the next one is " + Format.date(next)
        }
    }

    /** Adds a new subscription from the form. Call only when [problem] is null. */
    fun add(manager: SubscriptionManager, today: LocalDate): Subscription {
        val sub = manager.add(oneLine(name), parsedCost!!, cycle, date!!, oneLine(category).ifEmpty { "Other" })
        applyCurrency(sub, manager)
        sub.freeTrial = freeTrial
        sub.note = oneLine(note)
        sub.rollForward(today)
        return sub
    }

    /**
     * Saves the form into an existing subscription. Call only when [problem] is null.
     *
     * @return the price change that was recorded, or null if none was
     */
    fun applyTo(sub: Subscription, today: LocalDate, manager: SubscriptionManager? = null): PriceChange? {
        if (manager != null) applyCurrency(sub, manager)
        val newCost = parsedCost!!
        var recorded: PriceChange? = null
        if (newCost != sub.cost && recordPriceChange) {
            sub.changePrice(newCost, cycle, today)
            recorded = sub.priceChanges.last()
        } else {
            sub.cost = newCost
            sub.cycle = cycle
        }
        sub.name = oneLine(name)
        sub.freeTrial = freeTrial
        sub.nextPayment = date!!
        sub.category = oneLine(category).ifEmpty { "Other" }
        sub.note = oneLine(note)
        sub.rollForward(today)
        return recorded
    }

    /** Sets the subscription's currency, and the exchange rate for it. */
    private fun applyCurrency(sub: Subscription, manager: SubscriptionManager) {
        sub.currency = if (isForeign) currency else ""
        if (isForeign) manager.setRate(sub.currency, parsedRate!!)
    }

    companion object {
        /** A form filled in with a subscription's current values, for editing it. */
        fun from(s: Subscription, manager: SubscriptionManager? = null) = SubscriptionForm(
            name = s.name,
            cost = Money.toPlainString(s.cost),
            cycle = s.cycle,
            freeTrial = s.freeTrial,
            date = s.nextPayment,
            category = s.category,
            note = s.note,
            foreign = s.isForeign,
            currency = s.currency,
            rate = manager?.rates?.get(s.currency)?.let { Money.rateToString(it) } ?: "",
        )

        /**
         * Text on one line: line breaks and tabs become spaces, which the
         * data file needs (each subscription is one tab-separated line).
         */
        fun oneLine(text: String): String = text.replace(Regex("[\\t\\r\\n]+"), " ").trim()

        private fun dayName(day: DayOfWeek): String = day.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}
