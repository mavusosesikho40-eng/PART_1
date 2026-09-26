package subscriptiontracker.mobile.data

import kotlin.math.abs

/**
 * Amounts are kept as whole cents, so sums are exact: 199.00 is 19900.
 * The desktop app uses BigDecimal with two decimals; this gives the same
 * results, including rounding half up (away from zero).
 */
object Money {

    /** a / b rounded half up (away from zero), like BigDecimal's HALF_UP. */
    fun divideRounded(a: Long, b: Long): Long {
        require(b != 0L) { "divide by zero" }
        val negative = (a < 0) != (b < 0)
        val x = abs(a)
        val y = abs(b)
        val q = x / y
        val rounded = if ((x % y) * 2 >= y) q + 1 else q
        return if (negative) -rounded else rounded
    }

    /** "1,234.50" (no currency symbol), with a minus sign for negative amounts. */
    fun plain(cents: Long): String {
        val digits = abs(cents)
        val whole = (digits / 100).toString()
        val grouped = StringBuilder()
        for (i in whole.indices) {
            if (i > 0 && (whole.length - i) % 3 == 0) grouped.append(',')
            grouped.append(whole[i])
        }
        val fraction = (digits % 100).toString().padStart(2, '0')
        return (if (cents < 0) "-" else "") + grouped + "." + fraction
    }

    /** "1234.50", as saved in the data file and the CSV export. */
    fun toPlainString(cents: Long): String {
        val digits = abs(cents)
        return (if (cents < 0) "-" else "") + (digits / 100) + "." + (digits % 100).toString().padStart(2, '0')
    }

    /**
     * Reads a plain decimal such as "199", "199.5" or "1234.567" (as saved in
     * the data file), rounding to cents half up. Throws if it isn't a number.
     */
    fun parsePlain(text: String): Long {
        val match = Regex("""(-?)(\d*)(?:\.(\d*))?""").matchEntire(text)
            ?: throw NumberFormatException("not a number: $text")
        val (sign, whole, fraction) = match.destructured
        if (whole.isEmpty() && fraction.isEmpty()) throw NumberFormatException("not a number: $text")
        if (whole.length > 15) throw NumberFormatException("too large: $text")
        var cents = (whole.ifEmpty { "0" }.toLong()) * 100 + fraction.padEnd(2, '0').take(2).toLong()
        if (fraction.length > 2 && fraction[2] >= '5') cents++
        return if (sign == "-") -cents else cents
    }
}
