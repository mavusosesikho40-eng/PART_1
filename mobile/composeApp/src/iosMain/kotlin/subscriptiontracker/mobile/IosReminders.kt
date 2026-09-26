package subscriptiontracker.mobile

import kotlinx.datetime.number
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import subscriptiontracker.mobile.data.Dates
import subscriptiontracker.mobile.data.Reminders
import subscriptiontracker.mobile.data.SubscriptionManager

/**
 * Payment and free-trial reminders on iPhone: the next 30 days' reminders
 * are scheduled ahead at 9 in the morning, and scheduled again after every
 * change. The first time, iOS asks whether the app may send notifications.
 */
object IosReminders {

    /** iOS keeps at most 64 scheduled notifications per app. */
    private const val MOST = 60

    fun schedule(manager: SubscriptionManager) {
        val reminders = Reminders.upcoming(manager, Dates.today()).take(MOST)
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            if (granted) {
                center.removeAllPendingNotificationRequests()
                for (reminder in reminders) {
                    val content = UNMutableNotificationContent()
                    content.setTitle(reminder.title)
                    content.setBody(reminder.text)
                    content.setSound(UNNotificationSound.defaultSound())
                    val at = NSDateComponents()
                    at.setYear(reminder.on.year.toLong())
                    at.setMonth(reminder.on.month.number.toLong())
                    at.setDay(reminder.on.day.toLong())
                    at.setHour(9)
                    val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, false)
                    center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(reminder.id, content, trigger), null)
                }
            }
        }
    }
}
