package subscriptiontracker.mobile.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** How often a subscription is charged. */
enum class BillingCycle(val label: String, private val paymentsPerYear: Int) {
    WEEKLY("Weekly", 52),
    MONTHLY("Monthly", 12),
    QUARTERLY("Quarterly", 4),
    YEARLY("Yearly", 1);

    /** How often, as used after an amount: "a week", "a month", "a quarter" or "a year". */
    val per: String
        get() = when (this) {
            WEEKLY -> "a week"
            MONTHLY -> "a month"
            QUARTERLY -> "a quarter"
            YEARLY -> "a year"
        }

    /** Converts a cost per cycle into the equivalent yearly cost. */
    fun toYearly(cents: Long): Long = cents * paymentsPerYear

    /** Converts a cost per cycle into the equivalent monthly cost, rounded to the cent. */
    fun toMonthly(cents: Long): Long = Money.divideRounded(toYearly(cents), 12)

    /**
     * The payment date that follows the given one, for a subscription billed
     * on the given day of the month. In a month too short for that day the
     * last day of the month is used, and the next month goes back to the
     * billing day (31 Jan, 28 Feb, 31 Mar...). Weekly payments ignore it.
     */
    fun next(date: LocalDate, billingDay: Int = date.day): LocalDate {
        val next = when (this) {
            WEEKLY -> return date.plus(1, DateTimeUnit.WEEK)
            MONTHLY -> date.plus(1, DateTimeUnit.MONTH)
            QUARTERLY -> date.plus(3, DateTimeUnit.MONTH)
            YEARLY -> date.plus(1, DateTimeUnit.YEAR)
        }
        return LocalDate(next.year, next.month, minOf(billingDay, lengthOfMonth(next)))
    }

    companion object {
        fun lengthOfMonth(date: LocalDate): Int {
            val first = LocalDate(date.year, date.month, 1)
            return first.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY).day
        }
    }
}
