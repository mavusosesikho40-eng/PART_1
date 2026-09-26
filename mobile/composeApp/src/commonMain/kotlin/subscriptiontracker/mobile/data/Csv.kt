package subscriptiontracker.mobile.data

import kotlinx.datetime.LocalDate

/**
 * Reads subscriptions from CSV text: one exported by this app or the
 * desktop app, or a spreadsheet saved as CSV. The first row must name the
 * columns; only Name and Cost are required. Rows that can't be read, or
 * whose name is already in the list, are skipped with a reason.
 */
object CsvImporter {

    /** How many subscriptions were imported, and why any rows were skipped. */
    data class Result(val imported: Int, val problems: List<String>)

    private val COLUMN_NAMES: Map<String, String> = buildMap {
        fun column(field: String, vararg headers: String) = headers.forEach { put(it, field) }
        column("name", "name", "subscription", "service")
        column("cost", "cost", "price", "amount")
        column("cycle", "billing cycle", "cycle", "billing", "frequency")
        column("next", "next payment", "next payment date", "next due", "due date", "next billing date")
        column("category", "category")
        column("trial", "free trial", "trial")
        column("note", "note", "notes")
        column("currency", "currency")
    }

    fun importText(input: String, manager: SubscriptionManager, today: LocalDate): Result {
        val text = input.removePrefix("﻿")
        val rows = parse(text, detectDelimiter(text))
        if (rows.isEmpty()) return Result(0, listOf("The file is empty."))

        val columns = mutableMapOf<String, Int>()
        rows[0].forEachIndexed { i, header ->
            COLUMN_NAMES[header.trim().lowercase()]?.let { columns.getOrPut(it) { i } }
        }
        if ("name" !in columns || "cost" !in columns) {
            return Result(0, listOf("The first row must name the columns, including Name and Cost."))
        }

        val names = manager.all.map { it.name.lowercase() }.toMutableSet()
        val problems = mutableListOf<String>()
        var imported = 0
        for (r in 1 until rows.size) {
            val row = rows[r]
            if (row.all { it.isBlank() }) continue
            val rowNumber = r + 1
            fun cell(field: String): String {
                val index = columns[field] ?: return ""
                return if (index < row.size) row[index].trim() else ""
            }
            try {
                val name = cell("name").replace("\t", " ")
                require(name.isNotEmpty()) { "it has no name" }
                if (!names.add(name.lowercase())) {
                    problems += "Row $rowNumber: \"$name\" is already in your list."
                    continue
                }
                val cost = parseAmount(cell("cost"))
                val cycle = parseCycle(cell("cycle"))
                val date = cell("next")
                val next = if (date.isEmpty()) today else parseDate(date)
                val category = cell("category").replace("\t", " ")
                val sub = manager.add(name, cost, cycle, next, category.ifEmpty { "Other" })
                sub.freeTrial = cell("trial").lowercase() in setOf("yes", "y", "true", "1")
                sub.note = cell("note").replace("\t", " ")
                val currency = cell("currency")
                if (currency.isNotEmpty()) {
                    require(Format.isValidCurrencyCode(currency)) { "the currency \"$currency\" isn't a code like USD" }
                    sub.currency = currency
                }
                sub.rollForward(today)
                imported++
            } catch (e: IllegalArgumentException) {
                problems += "Row $rowNumber: ${e.message}."
            }
        }
        return Result(imported, problems)
    }

    /**
     * Reads an amount such as "199", "R 1,299.00", "$1 299.50", "1299,50" or
     * "1.299,50", in cents. When both a dot and a comma appear, whichever
     * comes last is the decimal point; a lone comma followed by one or two
     * digits at the end is a decimal comma; any other comma separates
     * thousands.
     */
    fun parseAmount(text: String): Long {
        var digits = text.replace(Regex("[^0-9.,\\-]"), "")
        val lastComma = digits.lastIndexOf(',')
        val lastDot = digits.lastIndexOf('.')
        digits = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) digits.replace(".", "").replace(',', '.') else digits.replace(",", "")
            lastComma >= 0 && Regex(".*,\\d{1,2}").matches(digits) -> digits.replace(',', '.')
            else -> digits.replace(",", "")
        }
        val cents = try {
            Money.parsePlain(digits)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("the cost \"$text\" isn't an amount")
        }
        require(cents >= 0 && !(cents == 0L && digits.startsWith("-") && digits.any { it in '1'..'9' })) {
            "the cost \"$text\" is negative"
        }
        return cents
    }

    fun parseCycle(text: String): BillingCycle = when (text.lowercase()) {
        "", "monthly", "month", "per month" -> BillingCycle.MONTHLY
        "weekly", "week", "per week" -> BillingCycle.WEEKLY
        "quarterly", "quarter", "per quarter" -> BillingCycle.QUARTERLY
        "yearly", "year", "annual", "annually", "per year" -> BillingCycle.YEARLY
        else -> throw IllegalArgumentException(
            "the billing cycle \"$text\" isn't weekly, monthly, quarterly or yearly")
    }

    /** Reads a date written as 2026-10-01, 2026/10/01 or 01/10/2026 (day first). */
    fun parseDate(text: String): LocalDate {
        val t = text.trim()
        try {
            Regex("""(\d{4})-(\d{2})-(\d{2})""").matchEntire(t)?.let { m ->
                val (y, mo, d) = m.destructured
                return LocalDate(y.toInt(), mo.toInt(), d.toInt())
            }
            Regex("""(\d{4})/(\d{1,2})/(\d{1,2})""").matchEntire(t)?.let { m ->
                val (y, mo, d) = m.destructured
                return LocalDate(y.toInt(), mo.toInt(), d.toInt())
            }
            Regex("""(\d{1,2})/(\d{1,2})/(\d{4})""").matchEntire(t)?.let { m ->
                val (d, mo, y) = m.destructured
                return LocalDate(y.toInt(), mo.toInt(), d.toInt())
            }
        } catch (e: IllegalArgumentException) {
            // Not a real day, e.g. 31/02/2026.
        }
        throw IllegalArgumentException("the date \"$text\" isn't like 2026-10-01 or 01/10/2026")
    }

    /** Picks whichever of comma, semicolon or tab appears most in the first line. */
    fun detectDelimiter(text: String): Char {
        val firstLine = text.substringBefore('\n')
        var best = ','
        var bestCount = firstLine.count { it == ',' }
        for (candidate in listOf(';', '\t')) {
            val count = firstLine.count { it == candidate }
            if (count > bestCount) {
                best = candidate
                bestCount = count
            }
        }
        return best
    }

    /**
     * Splits CSV text into rows of cells. Quoted cells may contain the
     * delimiter, line breaks and doubled quotes ("") for a quote.
     */
    fun parse(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"' && i + 1 < text.length && text[i + 1] == '"') {
                    cell.append('"')
                    i++
                } else if (c == '"') {
                    quoted = false
                } else {
                    cell.append(c)
                }
            } else if (c == '"') {
                quoted = true
            } else if (c == delimiter) {
                row += cell.toString()
                cell.clear()
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row += cell.toString()
                cell.clear()
                rows += row
                row = mutableListOf()
            } else {
                cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}

/** Writes subscriptions as CSV that spreadsheet programs (and both apps' import) can read. */
object CsvExporter {

    private const val HEADER =
        "ID,Name,Category,Cost,Billing Cycle,Next Payment,Monthly Cost,Yearly Cost,Free Trial,Note,Currency"

    fun toCsv(subscriptions: List<Subscription>): String {
        val csv = StringBuilder(HEADER).append("\r\n")
        for (s in subscriptions) {
            csv.append(listOf(
                s.id.toString(), escape(s.name), escape(s.category), Money.toPlainString(s.cost), s.cycle.label,
                s.nextPayment.toString(), Money.toPlainString(s.monthlyCost), Money.toPlainString(s.yearlyCost),
                if (s.freeTrial) "Yes" else "No", escape(s.note), s.currency,
            ).joinToString(",")).append("\r\n")
        }
        return csv.toString()
    }

    /** Quotes a value if it contains a comma, quote or line break. */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
