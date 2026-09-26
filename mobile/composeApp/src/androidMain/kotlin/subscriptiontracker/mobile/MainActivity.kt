package subscriptiontracker.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import subscriptiontracker.mobile.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Saved in the app's private storage, which Android backs up with the phone.
        val file = filesDir.toOkioPath() / "subscriptions.txt"
        setContent {
            App(FileSystem.SYSTEM, file)
        }
    }
}
