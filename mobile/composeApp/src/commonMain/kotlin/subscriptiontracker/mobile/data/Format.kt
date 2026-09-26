package subscriptiontracker.mobile.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** How amounts, dates and price changes are written on screen. */
class Format(val currencySymbol: String) {

    /**
     * An amount with the currency symbol, e.g. "R 1,234.50", "$1,234.50" or
     * "-R 30.00". Symbols ending in a letter get a space.
     */
    fun money(cents: Long): String {
        if (currencySymbol.isEmpty()) return Money.plain(cents)
        val gap = if (currencySymbol.last().isLetter()) " " else ""
        return (if (cents < 0) "-" else "") + currencySymbol + gap + Money.plain(if (cents < 0) -cents else cents)
    }

    /** An amount with a "+" in front when it's positive. */
    fun signedMoney(cents: Long): String = (if (cents > 0) "+" else "") + money(cents)

    /**
     * Describes a price change, e.g. "169.00 → 199.00 (+30.00, +18%)", or,
     * when the billing cycle changed too, per month:
     * "199.00 a month → 2,000.00 a year (-32.33 a month, -16%)".
     */
    fun describe(change: PriceChange): String {
        val percent = change.percent
        val percentText = if (percent == null) "" else ", " + (if (percent > 0) "+" else "") + percent + "%"
        return if (change.cycleChanged) {
            money(change.oldCost) + " " + change.oldCycle.per + " → " + money(change.newCost) + " " +
                change.newCycle.per + " (" + signedMoney(change.monthlyDifference) + " a month" + percentText + ")"
        } else {
            money(change.oldCost) + " → " + money(change.newCost) + " (" + signedMoney(change.difference) + percentText + ")"
        }
    }

    /** "88% of your 1,200.00 budget · 134.11 left", or a note that there's no budget. */
    fun budgetLine(monthly: Long, budget: Long?): String {
        if (budget == null) return "No monthly budget set"
        val percent = monthly * 100 / budget
        val left = budget - monthly
        val remaining = if (left >= 0) money(left) + " left" else money(-left) + " over"
        return (if (left < 0) "Over budget: " else "") + "$percent% of your ${money(budget)} budget · $remaining"
    }

    companion object {
        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        /** "1 Oct 2026". */
        fun date(date: LocalDate): String = "${date.day} ${MONTHS[date.month.ordinal]} ${date.year}"

        /** "1 Oct". */
        fun shortDate(date: LocalDate): String = "${date.day} ${MONTHS[date.month.ordinal]}"

        /** How far away a date is: "today", "tomorrow", "in 5 days", or "5 days ago". */
        fun dueIn(date: LocalDate, today: LocalDate): String {
            val days = today.daysUntil(date)
            return when {
                days == 0 -> "today"
                days == 1 -> "tomorrow"
                days > 1 -> "in $days days"
                days == -1 -> "yesterday"
                else -> "${-days} days ago"
            }
        }

        /** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st, 22nd, 23rd … 31st. */
        fun ordinal(day: Int): String {
            if (day in 11..13) return "${day}th"
            return day.toString() + when (day % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }

        /**
         * Whether a currency symbol can be used: up to 5 characters, with no
         * digits, spaces or the characters . , + - (which would read as part
         * of the amount).
         */
        fun isValidCurrencySymbol(symbol: String): Boolean =
            symbol.isNotEmpty() && symbol.length <= 5 &&
                symbol.none { it.isDigit() || it.isWhitespace() || it in ".,+-" }
    }
}
