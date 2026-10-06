package subscriptiontracker.mobile.data

/**
 * Spots what you changed, so synced copies can be merged: compares the
 * subscriptions and settings with how they were when last loaded or saved,
 * and stamps whatever differs with the time. Dates moving on by themselves
 * (rolling forward when the app opens) happen before the comparison point,
 * so they never count as a change and never outweigh a real edit made on
 * another device.
 */
class ChangeTracker {

    private var subscriptions: Map<String, String> = emptyMap()
    private var settings: String = ""

    /** Remembers [manager] as it is now: the point later changes are measured from. */
    fun remember(manager: SubscriptionManager) {
        subscriptions = manager.allIncludingCancelled.associate { it.uid to signature(it) }
        settings = settingsSignature(manager)
    }

    /**
     * Stamps every subscription and setting changed since [remember] with
     * [now], and records the removed ones as deleted at [now]. Then
     * remembers the result.
     */
    fun stamp(manager: SubscriptionManager, now: Long) {
        val current = manager.allIncludingCancelled
        for (s in current) {
            if (subscriptions[s.uid] != signature(s)) s.updated = now
        }
        val present = current.map { it.uid }.toSet()
        for (uid in subscriptions.keys) {
            if (uid !in present) manager.markDeleted(uid, now)
        }
        if (settingsSignature(manager) != settings) manager.settingsUpdated = now
        remember(manager)
    }

    private fun signature(s: Subscription) = Storage.subscriptionLine(s, sync = false).substringAfter('\t')

    private fun settingsSignature(m: SubscriptionManager) =
        m.currencySymbol + "|" + m.monthlyBudget + "|" + m.rates.entries.sortedBy { it.key }.joinToString()
}

/** Merges two copies of the subscriptions: this device's and the one in the sync file. */
object SyncMerge {

    /**
     * Each subscription comes from whichever copy changed it last (this
     * device's when they're level), with the payments recorded in both kept;
     * one deleted after its last change is left out. The currency, budget
     * and exchange rates come from whichever copy changed them last. This
     * device's numbers for its subscriptions are kept; ones new from the
     * other copy get new numbers.
     */
    fun merge(local: SubscriptionManager, remote: SubscriptionManager): SubscriptionManager {
        val out = SubscriptionManager()
        val settings = if (remote.settingsUpdated > local.settingsUpdated) remote else local
        out.currencySymbol = settings.currencySymbol
        out.monthlyBudget = settings.monthlyBudget
        settings.rates.forEach { (code, micro) -> out.setRate(code, micro) }
        out.settingsUpdated = maxOf(local.settingsUpdated, remote.settingsUpdated)
        (local.deleted.entries + remote.deleted.entries).forEach { (uid, at) -> out.markDeleted(uid, at) }

        val mine = local.allIncludingCancelled.associateBy { it.uid }
        val theirs = remote.allIncludingCancelled.associateBy { it.uid }
        var nextId = (local.allIncludingCancelled.maxOfOrNull { it.id } ?: 0) + 1
        for (uid in (mine.keys + theirs.keys)) {
            val l = mine[uid]
            val r = theirs[uid]
            val winner = when {
                l == null -> r!!
                r == null -> l
                r.updated > l.updated -> r
                else -> l
            }
            val deletedAt = out.deleted[uid]
            if (deletedAt != null && deletedAt >= winner.updated) continue
            val merged = winner.copy(id = l?.id ?: nextId++)
            // Keep every payment either copy recorded; one you confirmed beats one at the list price.
            val other = if (winner === l) r else l
            other?.payments?.forEach { p ->
                val existing = merged.payments.firstOrNull { it.date == p.date }
                if (existing == null || (!existing.confirmed && p.confirmed)) merged.restorePayment(p)
            }
            out.restore(merged)
        }
        return out
    }

    /**
     * Whether two copies hold the same subscriptions and settings (so
     * nothing needs saving), ignoring each device's own numbers for them.
     */
    fun same(a: SubscriptionManager, b: SubscriptionManager): Boolean = normalized(a) == normalized(b)

    private fun normalized(m: SubscriptionManager): List<String> =
        Storage.format(m).lines().map { line ->
            val first = line.substringBefore('\t')
            if (first.isNotEmpty() && first.all { it.isDigit() }) line.substringAfter('\t') else line
        }.sorted()
}
