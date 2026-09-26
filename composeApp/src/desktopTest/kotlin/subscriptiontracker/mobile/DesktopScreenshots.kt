package subscriptiontracker.mobile

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.vinceglb.filekit.FileKit
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.EncodedImageFormat
import subscriptiontracker.mobile.ui.App

/**
 * Draws the desktop app with sample data, at a desktop window's size and a
 * phone's, into build/desktop-screenshots, so each change can be looked at.
 */
class DesktopScreenshots {

    private fun day(days: Long) = LocalDate.now().plusDays(days).toString()

    private fun sampleFile(): File {
        val dir = Files.createTempDirectory("subscriptions").toFile()
        val file = File(dir, "subscriptions.txt")
        file.writeText(
            "BUDGET\t1600.00\nCURRENCY\tR\nRATE\tUSD\t18.25\n" +
                "1\tNetflix\t199.00\tMONTHLY\t${day(5)}\tEntertainment\tNOTE=Shared with family\tPRICE=${day(-180)}:169.00:199.00\n" +
                "2\tSpotify\t79.99\tMONTHLY\t${day(1)}\tMusic\tPAID=${day(-59)}:79.99:assumed\tPAID=${day(-29)}:84.99\n" +
                "9\tChatBot Plus\t20.00\tMONTHLY\t${day(4)}\tSoftware\tCUR=USD\tPAID=${day(-26)}:20.00:assumed\n" +
                "3\tMicrosoft 365\t1099.00\tYEARLY\t${day(111)}\tSoftware\n" +
                "4\tDisney+\t99.00\tMONTHLY\t${day(2)}\tEntertainment\tTRIAL\n" +
                "5\tGym\t450.00\tMONTHLY\t${day(7)}\tHealth\n" +
                "7\tShowmax\t99.00\tMONTHLY\t${day(-100)}\tEntertainment\tCANCELLED=${day(-110)}\n" +
                "8\tCoffee club\t40.00\tWEEKLY\t${day(3)}\tFood\n",
        )
        return file
    }

    @Test
    fun drawEveryScreen() {
        FileKit.init(appId = "SubscriptionTrackerScreenshots")
        val out = File("build/desktop-screenshots").apply { mkdirs() }
        // Taller than a real window, so each picture shows the whole screen.
        val sizes = listOf("desktop" to (1200 to 1500), "phone" to (412 to 1500))
        for ((sizeName, size) in sizes) {
            for (screen in listOf("subscriptions", "upcoming", "spending", "cancelled", "add")) {
                val file = sampleFile()
                val scene = ImageComposeScene(size.first, size.second, Density(1f)) {
                    App(FileSystem.SYSTEM, file.toPath().toOkioPath(), startScreen = screen)
                }
                try {
                    scene.render(0)
                    scene.render(500_000_000)
                    val image = scene.render(1_000_000_000)
                    val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
                    File(out, "$sizeName-$screen.png").writeBytes(png)
                } finally {
                    scene.close()
                }
            }
        }
        assertTrue(File(out, "desktop-subscriptions.png").length() > 0)
    }
}
