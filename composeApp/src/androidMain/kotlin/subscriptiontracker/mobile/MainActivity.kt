package subscriptiontracker.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import okio.FileSystem
import subscriptiontracker.mobile.ui.App

class MainActivity : ComponentActivity() {

    private val askForNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // White status-bar icons: the top of every screen is black.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        PhoneData.scheduleDailyCheck(this)
        // Android 13 and later ask before an app can show notifications.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val startScreen = intent.getStringExtra("screen")
        setContent {
            App(
                fileSystem = FileSystem.SYSTEM,
                file = PhoneData.file(this),
                onSaved = { PhoneData.saved(applicationContext) },
                startScreen = startScreen,
            )
        }
    }
}
