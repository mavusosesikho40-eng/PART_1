package subscriptiontracker.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.IOException
import okio.Path
import subscriptiontracker.mobile.data.Backup
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
class AppState(fileSystem: FileSystem, file: Path, private val onSaved: (SubscriptionManager) -> Unit = {}) {

    private val storage = Storage(fileSystem, file)

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
            if (manager.rollForwardAll(today) > 0) save()
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
    }

    private fun save(): Boolean {
        if (loadError != null) return false
        return try {
            storage.save(manager)
            onSaved(manager)
            true
        } catch (e: IOException) {
            false
        }
    }

    /** The subscription being added (null inside) or edited, or null when the form is closed. */
    var editing by mutableStateOf<Editing?>(null)

    class Editing(val subscription: Subscription?)
}
