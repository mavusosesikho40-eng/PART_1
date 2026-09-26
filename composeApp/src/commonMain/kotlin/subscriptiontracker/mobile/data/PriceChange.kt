package subscriptiontracker.mobile.data

import kotlinx.datetime.LocalDate

/**
 * A change to what a subscription costs, e.g. Netflix going from 169.00 to
 * 199.00 a month. The billing cycle is kept too, because a plan can change
 * from monthly to yearly at the same time.
 */
data class PriceChange(
    val date: LocalDate,
    val oldCost: Long,
    val newCost: Long,
    val oldCycle: BillingCycle,
    val newCycle: BillingCycle = oldCycle,
) {
    val cycleChanged: Boolean get() = oldCycle != newCycle

    /** How much more (or, if negative, less) each payment costs now. */
    val difference: Long get() = newCost - oldCost

    /** How much more (or, if negative, less) it costs per month now. */
    val monthlyDifference: Long get() = newCycle.toMonthly(newCost) - oldCycle.toMonthly(oldCost)

    /**
     * The change as a whole-number percentage: of the old price per payment,
     * or of the old monthly cost when the billing cycle changed too. Null if
     * the old price was zero.
     */
    val percent: Long?
        get() {
            val before = if (cycleChanged) oldCycle.toMonthly(oldCost) else oldCost
            val change = if (cycleChanged) monthlyDifference else difference
            return if (before == 0L) null else Money.divideRounded(change * 100, before)
        }
}
