package subscriptiontracker.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import subscriptiontracker.mobile.data.Dates
import subscriptiontracker.mobile.data.Reminder
import subscriptiontracker.mobile.data.Reminders
import subscriptiontracker.mobile.data.Storage
import subscriptiontracker.mobile.data.SubscriptionManager

/**
 * The phone's side of the data: where it's saved, the daily check that
 * posts reminders, and keeping the home-screen widget up to date.
 */
object PhoneData {

    private const val CHANNEL = "reminders"
    private const val DAILY = "daily-reminders"
    private const val NOW = "reminders-now"

    /** Saved in the app's private storage, which Android backs up with the phone. */
    fun file(context: Context): Path = context.filesDir.toOkioPath() / "subscriptions.txt"

    /** The subscriptions as saved, with overdue dates moved on (not saved; the app does that). */
    fun load(context: Context): SubscriptionManager {
        val manager = SubscriptionManager()
        try {
            Storage(FileSystem.SYSTEM, file(context)).load(manager)
        } catch (e: IOException) {
            return SubscriptionManager()
        }
        manager.rollForwardAll(Dates.today())
        return manager
    }

    /** Checks for reminders every day at about 9 in the morning. */
    fun scheduleDailyCheck(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** After a save: check for today's reminders now (e.g. something just added is due tomorrow). */
    fun saved(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    /** Posts today's reminders that haven't been shown yet, and refreshes the widget. */
    suspend fun check(context: Context) {
        val manager = load(context)
        val today = Dates.today()
        val upcoming = Reminders.upcoming(manager, today)
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val shown = prefs.getStringSet("shown", emptySet()).orEmpty()
        val due = upcoming.filter { it.on == today && it.id !in shown }
        if (due.isNotEmpty() && canNotify(context)) {
            createChannel(context)
            due.forEach { post(context, it) }
            // Remember what was shown, forgetting reminders that are over.
            val ids = upcoming.map { it.id }.toSet()
            prefs.edit().putStringSet("shown", (shown.filter { it in ids } + due.map { it.id }).toSet()).apply()
        }
        SubscriptionWidget().updateAll(context)
    }

    private fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Payment reminders", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "The day before a payment, and before a free trial ends"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun post(context: Context, reminder: Reminder) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }
}

/** Runs [PhoneData.check] in the background, daily and after each save. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PhoneData.check(applicationContext)
        return Result.success()
    }
}
