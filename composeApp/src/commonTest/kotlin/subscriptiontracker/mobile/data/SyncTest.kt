package subscriptiontracker.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class ChangeTrackerTest {

    private val today = LocalDate(2026, 10, 6)

    @Test
    fun onlyRealChangesAreStamped() {
        val m = SubscriptionManager()
        val netflix = m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 9, 1), "Streaming")
        val gym = m.add("Gym", 45000, BillingCycle.MONTHLY, LocalDate(2026, 10, 20), "Health")
        // Opening the app rolls dates forward first; that isn't a change of yours.
        m.rollForwardAll(today)
        val tracker = ChangeTracker()
        tracker.remember(m)

        gym.name = "Gym (family)"
        tracker.stamp(m, now = 1000)

        assertEquals(0, netflix.updated)
        assertEquals(1000, gym.updated)
        assertEquals(0, m.settingsUpdated)

        tracker.stamp(m, now = 2000)
        assertEquals(1000, gym.updated, "Nothing changed since, so nothing is stamped again")
    }

    @Test
    fun removalsAndSettingsAreStamped() {
        val m = SubscriptionManager()
        val netflix = m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 11, 1), "Streaming")
        val tracker = ChangeTracker()
        tracker.remember(m)

        m.remove(netflix.id)
        m.monthlyBudget = 150000
        tracker.stamp(m, now = 5000)

        assertEquals(mapOf(netflix.uid to 5000L), m.deleted)
        assertEquals(5000, m.settingsUpdated)
    }
}

class SyncMergeTest {

    private fun sub(uid: String, id: Int, name: String, updated: Long, cost: Long = 10000) =
        Subscription(id, name, cost, BillingCycle.MONTHLY, LocalDate(2026, 11, 1), "X").also {
            it.uid = uid
            it.updated = updated
        }

    private fun manager(vararg subs: Subscription) = SubscriptionManager().apply { subs.forEach { restore(it) } }

    @Test
    fun theNewerChangeWinsAndNewOnesComeAcross() {
        val local = manager(sub("aaaa", 1, "Netflix", updated = 100), sub("bbbb", 2, "Gym", updated = 900))
        val remote = manager(sub("aaaa", 7, "Netflix HD", updated = 500), sub("bbbb", 8, "Gym old", updated = 300),
            sub("cccc", 9, "Spotify", updated = 400))

        val merged = SyncMerge.merge(local, remote)

        assertEquals("Netflix HD", merged.allIncludingCancelled.first { it.uid == "aaaa" }.name)
        assertEquals("Gym", merged.allIncludingCancelled.first { it.uid == "bbbb" }.name)
        // This device's numbers are kept; the new one gets the next free number.
        assertEquals(mapOf("aaaa" to 1, "bbbb" to 2, "cccc" to 3), merged.allIncludingCancelled.associate { it.uid to it.id })
    }

    @Test
    fun aTieKeepsThisDevicesCopy() {
        val merged = SyncMerge.merge(manager(sub("aaaa", 1, "Mine", 100)), manager(sub("aaaa", 1, "Theirs", 100)))
        assertEquals("Mine", merged.all.single().name)
    }

    @Test
    fun aDeletionWinsOverOlderChangesButNotNewerOnes() {
        val local = manager(sub("aaaa", 1, "Netflix", updated = 100), sub("bbbb", 2, "Gym", updated = 800))
        val remote = manager().apply {
            markDeleted("aaaa", 200)
            markDeleted("bbbb", 300)
        }

        val merged = SyncMerge.merge(local, remote)

        assertEquals(listOf("Gym"), merged.all.map { it.name }, "Gym was changed after it was deleted elsewhere")
        assertEquals(mapOf("aaaa" to 200L, "bbbb" to 300L), merged.deleted)
        // And the deletion travels on, so a third copy drops it too.
        assertTrue(SyncMerge.merge(manager(sub("aaaa", 4, "Netflix", 50)), merged).all.none { it.uid == "aaaa" })
    }

    @Test
    fun paymentsFromBothCopiesAreKeptPreferringConfirmedOnes() {
        val mine = sub("aaaa", 1, "Netflix", updated = 900).apply {
            restorePayment(Paid(LocalDate(2026, 8, 1), 10000, confirmed = false))
            restorePayment(Paid(LocalDate(2026, 9, 1), 10000, confirmed = false))
        }
        val theirs = sub("aaaa", 1, "Netflix", updated = 100).apply {
            restorePayment(Paid(LocalDate(2026, 7, 1), 10000, confirmed = false))
            restorePayment(Paid(LocalDate(2026, 9, 1), 12500, confirmed = true))
        }

        val merged = SyncMerge.merge(manager(mine), manager(theirs)).all.single()

        assertEquals(listOf(
            Paid(LocalDate(2026, 7, 1), 10000, false),
            Paid(LocalDate(2026, 8, 1), 10000, false),
            Paid(LocalDate(2026, 9, 1), 12500, true),
        ), merged.payments)
    }

    @Test
    fun settingsComeFromWhicheverWasChangedLast() {
        val local = SubscriptionManager().apply {
            currencySymbol = "R"
            monthlyBudget = 100000
            settingsUpdated = 100
        }
        val remote = SubscriptionManager().apply {
            currencySymbol = "R"
            monthlyBudget = 150000
            setRate("USD", 18_000_000)
            settingsUpdated = 200
        }

        val merged = SyncMerge.merge(local, remote)

        assertEquals(150000, merged.monthlyBudget)
        assertEquals(mapOf("USD" to 18_000_000L), merged.rates)
        assertEquals(200, merged.settingsUpdated)
    }

    @Test
    fun copiesThatOnlyDifferInTheirNumbersAreTheSame() {
        val a = manager(sub("aaaa", 1, "Netflix", 100))
        val b = manager(sub("aaaa", 5, "Netflix", 100))
        assertTrue(SyncMerge.same(a, b))
        assertFalse(SyncMerge.same(a, manager(sub("aaaa", 1, "Netflix", 101))))
    }
}

class SyncStorageTest {

    @Test
    fun idsChangeTimesDeletionsAndSettingsTimeAreSaved() {
        val m = SubscriptionManager()
        m.add("Netflix", 19900, BillingCycle.MONTHLY, LocalDate(2026, 11, 1), "Streaming").apply {
            uid = "0123456789abcdef"
            updated = 1_790_000_000_000
        }
        m.markDeleted("fedcba9876543210", 1_789_000_000_000)
        m.settingsUpdated = 1_788_000_000_000

        val text = Storage.format(m)
        assertTrue(text.contains("\tUID=0123456789abcdef\tUPDATED=1790000000000"))
        assertTrue(text.contains("DELETED\tfedcba9876543210\t1789000000000\n"))
        assertTrue(text.contains("SETTINGS\t1788000000000\n"))

        val copy = SubscriptionManager()
        assertEquals(0, Storage.parse(text, copy))
        assertTrue(SyncMerge.same(m, copy))
    }

    @Test
    fun readingStopsAtTheEndOfASyncFile() {
        val m = SubscriptionManager()
        val text = "1\tNetflix\t199.00\tMONTHLY\t2026-11-01\tStreaming\n" + Storage.END + "\n" +
            "2\tLeft over\t5.00\tMONTHLY\t2026-11-01\tX\n"
        assertEquals(0, Storage.parse(text, m))
        assertEquals(listOf("Netflix"), m.all.map { it.name })
    }

    @Test
    fun aFileWithoutIdsGetsThemAndSaysSo() {
        val fs = FakeFileSystem()
        fs.createDirectories("/d".toPath())
        val storage = Storage(fs, "/d/s.txt".toPath())
        fs.write("/d/s.txt".toPath()) { writeUtf8("1\tNetflix\t199.00\tMONTHLY\t2026-11-01\tStreaming\n") }

        val m = SubscriptionManager()
        assertTrue(storage.load(m).newIds)
        assertEquals(16, m.all.single().uid.length)

        storage.save(m)
        val again = SubscriptionManager()
        assertFalse(storage.load(again).newIds)
        assertEquals(m.all.single().uid, again.all.single().uid)
    }
}
