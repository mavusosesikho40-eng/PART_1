package subscriptiontracker.mobile

import androidx.compose.ui.window.ComposeUIViewController
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import subscriptiontracker.mobile.ui.App

/** Called from Swift (ContentView.swift) to show the app. */
fun MainViewController() = ComposeUIViewController {
    // Saved in the app's Documents folder, which iOS backs up with the phone.
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    App(FileSystem.SYSTEM, documents.toPath() / "subscriptions.txt", onSaved = { IosReminders.schedule(it) })
}
