package subscriptiontracker.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.IOException
import okio.Path
import io.github.vinceglb.filekit.BookmarkData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import subscriptiontracker.mobile.data.Backup
import subscriptiontracker.mobile.data.ChangeTracker
import subscriptiontracker.mobile.data.SyncMerge
import subscriptiontracker.mobile.data.TextDecoding
import subscriptiontracker.mobile.data.Dates
import subscriptiontracker.mobile.data.Format
import subscriptiontracker.mobile.data.Storage
import subscriptiontracker.mobile.data.Subscription
import subscriptiontracker.mobile.data.SubscriptionManager

/**
 * The subscriptions and the file they're saved in. Every change goes
 * through [changed], which saves straight away and makes every screen
 * redraw (they read [version]). [onSaved] is told after every save, so the
 * phone can refresh its widget and reminders.
 */
class AppState(private val fileSystem: FileSystem, file: Path, private val onSaved: (SubscriptionManager) -> Unit = {}) {

    private val storage = Storage(fileSystem, file)
    private val tracker = ChangeTracker()

    /** Where the bookmark to the sync file is kept, next to the data file. */
    private val syncBookmarkFile = file.parent!! / (file.name + ".sync")

    var manager by mutableStateOf(SubscriptionManager())
        private set

    /** Bumped on every change, so screens that read it redraw. */
    var version by mutableIntStateOf(0)
        private set

    /** A short message to show at the bottom of the screen, or null. */
    var message by mutableStateOf<String?>(null)

    /** Set if the data file couldn't be read; nothing is saved then, so it isn't overwritten. */
    var loadError by mutableStateOf<String?>(null)
        private set

    val today: LocalDate get() = Dates.today()

    val format: Format get() = Format(manager.currencySymbol)

    /**
     * True on the very first start, when there's no data file yet: the app
     * then shows the setup steps. Finishing them (or restoring a backup)
     * saves, which creates the file, so they don't appear again.
     */
    var settingUp by mutableStateOf(!fileSystem.exists(file))

    init {
        try {
            val result = storage.load(manager)
            if (result.skippedLines > 0) {
                message = "Skipped ${result.skippedLines} line(s) that couldn't be read; " +
                    "the file was copied to ${result.unreadableCopy?.name} first."
            }
            // Dates moving on, and ids given to an older file, are saved without counting as changes.
            if (manager.rollForwardAll(today) > 0 || result.newIds) storage.save(manager)
            tracker.remember(manager)
            onSaved(manager)
        } catch (e: IOException) {
            loadError = "Couldn't read your subscriptions (${e.message}). Nothing will be saved until the app is restarted."
            settingUp = false
        }
    }

    /** Saves what was chosen during setup and goes to the app. */
    fun finishSetup(currencySymbol: String, monthlyBudget: Long?) {
        manager.currencySymbol = currencySymbol
        manager.monthlyBudget = monthlyBudget
        settingUp = false
        changed("You're all set.")
    }

    /** Replaces everything with a backup's contents, then saves. */
    fun restore(backup: Backup) {
        manager = backup.manager
        settingUp = false
        changed("Restored ${backup.manager.all.size} subscription(s)" +
            (if (backup.manager.cancelled.isEmpty()) "" else " and ${backup.manager.cancelled.size} cancelled") + ".")
    }

    /** Saves after a change, redraws, and shows [text] (or why saving failed). */
    fun changed(text: String?) {
        message = if (save()) text else "Not saved: " + (loadError ?: "the file couldn't be written.")
        version++
        if (syncFileName != null) syncRequests++
    }

    private fun save(): Boolean {
        if (loadError != null) return false
        return try {
            tracker.stamp(manager, Dates.nowMillis())
            storage.save(manager)
            onSaved(manager)
            true
        } catch (e: IOException) {
            false
        }
    }

    // ---- Syncing through a file in a cloud drive ----

    /** The sync file's name, or null when not syncing. */
    var syncFileName by mutableStateOf<String?>(null)
        private set

    /** How the last sync went, e.g. "Synced at 14:02", or null before the first. */
    var syncStatus by mutableStateOf<String?>(null)
        private set

    /** Bumped after each change while syncing, so the app syncs shortly after. */
    var syncRequests by mutableIntStateOf(0)
        private set

    private fun syncBookmark(): ByteArray? =
        if (fileSystem.exists(syncBookmarkFile)) fileSystem.read(syncBookmarkFile) { readByteArray() } else null

    init {
        syncFileName = syncBookmark()?.let {
            try {
                PlatformFile.fromBookmarkData(BookmarkData(it)).name
            } catch (e: Exception) {
                "your sync file"
            }
        }
    }

    /** Syncs with [target] from now on (after it was picked or created), starting now. */
    suspend fun useSyncFile(target: PlatformFile) {
        try {
            val bookmark = target.bookmarkData().bytes
            fileSystem.write(syncBookmarkFile) { write(bookmark) }
            syncFileName = target.name
            syncNow()
        } catch (e: Exception) {
            syncStatus = "Couldn't use that file: ${e.message}"
        }
    }

    /** Stops syncing. The sync file itself is left as it is. */
    fun stopSyncing() {
        fileSystem.delete(syncBookmarkFile, mustExist = false)
        syncFileName = null
        syncStatus = null
    }

    /**
     * Reads the sync file, merges it with this device's subscriptions
     * (whichever copy changed each one last wins), saves the result here,
     * and writes it back to the sync file.
     */
    suspend fun syncNow() {
        val bookmark = syncBookmark() ?: return
        if (loadError != null) return
        try {
            val target = PlatformFile.fromBookmarkData(BookmarkData(bookmark))
            val bytes = target.readBytes()
            val remote = SubscriptionManager()
            Storage.parse(TextDecoding.decode(bytes), remote)
            // Anything changed here since the last save carries its time into the merge.
            tracker.stamp(manager, Dates.nowMillis())
            val merged = SyncMerge.merge(manager, remote)
            merged.rollForwardAll(today)
            if (!SyncMerge.same(merged, manager)) {
                manager = merged
                storage.save(merged)
                tracker.remember(merged)
                onSaved(merged)
                version++
            }
            if (bytes.isEmpty() || !SyncMerge.same(merged, remote)) {
                target.write((Storage.format(merged) + Storage.END + "\n").encodeToByteArray())
            }
            syncStatus = "Synced at " + Dates.timeNow()
        } catch (e: Exception) {
            syncStatus = "Couldn't sync: ${e.message ?: "the file couldn't be read or written."}"
        }
    }

    /** The subscription being added (null inside) or edited, or null when the form is closed. */
    var editing by mutableStateOf<Editing?>(null)

    class Editing(val subscription: Subscription?)
}
