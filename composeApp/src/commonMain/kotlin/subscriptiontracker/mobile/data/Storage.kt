package subscriptiontracker.mobile.data

import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Saves and loads subscriptions as a tab-separated text file, the same
 * format the desktop app uses. Each line holds: id, name, cost, cycle, next
 * payment date, category, then optional flags: "TRIAL", "CANCELLED=date",
 * "DAY=n", "NOTE=text" and "PRICE=date:old:new[:OLDCYCLE:NEWCYCLE]". A
 * monthly budget is saved on a "BUDGET" line and a currency symbol on a
 * "CURRENCY" line.
 *
 * Saving never leaves a half-written file: the new contents go to a
 * temporary file first, the previous file is kept as ".bak", and only then
 * is the new file moved into place.
 */
class Storage(private val fileSystem: FileSystem, val file: Path) {

    /** What loading found. [skippedLines] couldn't be read; the file was copied to [unreadableCopy] first. */
    data class LoadResult(val skippedLines: Int, val unreadableCopy: Path?)

    val backupFile: Path get() = sibling(".bak")

    private fun sibling(suffix: String): Path = file.parent!! / (file.name + suffix)

    fun save(manager: SubscriptionManager) {
        val temp = sibling(".tmp")
        try {
            fileSystem.write(temp, mustCreate = false) { writeUtf8(format(manager)) }
        } catch (e: IOException) {
            fileSystem.delete(temp, mustExist = false)
            throw e
        }
        if (fileSystem.exists(file)) {
            fileSystem.delete(backupFile, mustExist = false)
            fileSystem.copy(file, backupFile)
        }
        fileSystem.atomicMove(temp, file)
    }

    /**
     * Loads saved subscriptions into the manager. Lines that can't be read
     * are skipped; if there are any, the file is first copied, exactly as it
     * is, to a ".unreadable" file so they aren't lost when it's next saved.
     */
    fun load(manager: SubscriptionManager): LoadResult {
        if (!fileSystem.exists(file)) return LoadResult(0, null)
        val text = TextDecoding.decode(fileSystem.read(file) { readByteArray() })
        val skipped = parse(text, manager)
        var copy: Path? = null
        if (skipped > 0) {
            copy = sibling(".unreadable")
            fileSystem.delete(copy, mustExist = false)
            fileSystem.copy(file, copy)
        }
        return LoadResult(skipped, copy)
    }

    companion object {
        /** The text that [save] writes, one line per subscription. */
        fun format(manager: SubscriptionManager): String {
            val lines = mutableListOf<String>()
            manager.monthlyBudget?.let { lines += BUDGET + SEP + Money.toPlainString(it) }
            if (manager.currencySymbol.isNotEmpty()) lines += CURRENCY + SEP + manager.currencySymbol
            for ((code, micro) in manager.rates.entries.sortedBy { it.key }) lines += RATE + SEP + code + SEP + Money.rateToString(micro)
            for (s in manager.allIncludingCancelled) {
                val line = StringBuilder(listOf(
                    s.id.toString(), s.name, Money.toPlainString(s.cost), s.cycle.name, s.nextPayment.toString(), s.category,
                ).joinToString(SEP))
                if (s.freeTrial) line.append(SEP).append(TRIAL)
                s.cancelledOn?.let { line.append(SEP).append(CANCELLED).append(it) }
                if (s.billingDay != s.nextPayment.day) line.append(SEP).append(DAY).append(s.billingDay)
                if (s.hasNote) line.append(SEP).append(NOTE).append(s.note)
                if (s.isForeign) line.append(SEP).append(CUR).append(s.currency)
                for (change in s.priceChanges) {
                    line.append(SEP).append(PRICE).append(change.date).append(':')
                        .append(Money.toPlainString(change.oldCost)).append(':').append(Money.toPlainString(change.newCost))
                    if (change.cycleChanged) line.append(':').append(change.oldCycle.name).append(':').append(change.newCycle.name)
                }
                for (p in s.payments) {
                    line.append(SEP).append(PAID).append(p.date).append(':').append(Money.toPlainString(p.amount))
                    if (!p.confirmed) line.append(":assumed")
                }
                lines += line.toString()
            }
            return lines.joinToString("") { it + "\n" }
        }

        /** Reads the data file's text into the manager, returning how many lines were skipped. */
        fun parse(text: String, manager: SubscriptionManager): Int {
            var skipped = 0
            for (line in text.split(Regex("\r\n|\r|\n"))) {
                if (line.isBlank()) continue
                val parts = line.split(SEP)
                try {
                    when (parts[0]) {
                        BUDGET -> manager.monthlyBudget = Money.parsePlain(parts[1])
                        CURRENCY -> manager.currencySymbol = parts[1]
                        RATE -> manager.setRate(parts[1], Money.parseScaled(parts[2], 6))
                        else -> manager.restore(parseSubscription(parts))
                    }
                } catch (e: IllegalArgumentException) {
                    skipped++
                } catch (e: IndexOutOfBoundsException) {
                    skipped++
                }
            }
            return skipped
        }

        private fun parseSubscription(parts: List<String>): Subscription {
            val sub = Subscription(
                parts[0].toInt(), parts[1], Money.parsePlain(parts[2]), BillingCycle.valueOf(parts[3]),
                LocalDate.parse(parts[4]), parts[5],
            )
            for (part in parts.drop(6)) {
                when {
                    part == TRIAL -> sub.freeTrial = true
                    part.startsWith(CANCELLED) -> sub.cancel(LocalDate.parse(part.removePrefix(CANCELLED)))
                    part.startsWith(DAY) -> sub.restoreBillingDay(part.removePrefix(DAY).toInt())
                    part.startsWith(NOTE) -> sub.note = part.removePrefix(NOTE)
                    part.startsWith(CUR) -> sub.currency = part.removePrefix(CUR)
                    part.startsWith(PAID) -> {
                        val p = part.removePrefix(PAID).split(":")
                        sub.restorePayment(Paid(LocalDate.parse(p[0]), Money.parsePlain(p[1]), confirmed = p.getOrNull(2) != "assumed"))
                    }
                    part.startsWith(PRICE) -> {
                        val price = part.removePrefix(PRICE).split(":")
                        val oldCycle = if (price.size > 3) BillingCycle.valueOf(price[3]) else sub.cycle
                        val newCycle = if (price.size > 4) BillingCycle.valueOf(price[4]) else sub.cycle
                        sub.restorePriceChange(PriceChange(LocalDate.parse(price[0]),
                            Money.parsePlain(price[1]), Money.parsePlain(price[2]), oldCycle, newCycle))
                    }
                }
            }
            return sub
        }

        private const val SEP = "\t"
        private const val BUDGET = "BUDGET"
        private const val CURRENCY = "CURRENCY"
        private const val TRIAL = "TRIAL"
        private const val CANCELLED = "CANCELLED="
        private const val PRICE = "PRICE="
        private const val NOTE = "NOTE="
        private const val DAY = "DAY="
        private const val CUR = "CUR="
        private const val PAID = "PAID="
        private const val RATE = "RATE"
    }
}
