package subscriptiontracker.mobile

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vinceglb.filekit.FileKit
import org.jetbrains.compose.resources.painterResource
import subscriptiontracker.mobile.resources.Res
import subscriptiontracker.mobile.resources.app_icon
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import subscriptiontracker.mobile.ui.App

/**
 * The desktop app. The subscriptions are kept in SubscriptionTracker/subscriptions.txt
 * in your home folder, or in the file given as the first argument.
 */
fun main(args: Array<String>) {
    FileKit.init(appId = "SubscriptionTracker")
    val file = args.firstOrNull()?.toPath() ?: defaultFile()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Subscription Tracker",
            icon = painterResource(Res.drawable.app_icon),
            state = rememberWindowState(width = 1100.dp, height = 820.dp),
        ) {
            App(FileSystem.SYSTEM, file)
        }
    }
}

/**
 * ~/SubscriptionTracker/subscriptions.txt. The first time, if there's a
 * subscriptions.txt where the app was started (where the old desktop app
 * kept it), it's copied there so nothing is lost.
 */
private fun defaultFile(): Path {
    val fs = FileSystem.SYSTEM
    val dir = System.getProperty("user.home").toPath() / "SubscriptionTracker"
    fs.createDirectories(dir)
    val file = dir / "subscriptions.txt"
    val old = "subscriptions.txt".toPath()
    if (!fs.exists(file) && fs.exists(old)) fs.copy(old, file)
    return file
}
