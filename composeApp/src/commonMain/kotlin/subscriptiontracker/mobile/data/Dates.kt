package subscriptiontracker.mobile.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/** Dates as the phone and the date picker see them. */
object Dates {

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    /** Today on this phone. */
    @OptIn(ExperimentalTime::class)
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Now, in milliseconds since 1970: when a change was made, for merging synced copies. */
    @OptIn(ExperimentalTime::class)
    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /** The time on this device, as "14:02". */
    @OptIn(ExperimentalTime::class)
    fun timeNow(): String {
        val t = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
    }

    /** The date picker works in milliseconds since 1970 at midnight UTC. */
    fun toPickerMillis(date: LocalDate): Long = date.toEpochDays() * MILLIS_PER_DAY

    fun fromPickerMillis(millis: Long): LocalDate = LocalDate.fromEpochDays(millis.floorDiv(MILLIS_PER_DAY))
}
