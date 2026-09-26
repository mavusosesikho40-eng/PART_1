package subscriptiontracker.mobile.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Dates as the phone and the date picker see them. */
object Dates {

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    /** Today on this phone. */
    @OptIn(ExperimentalTime::class)
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** The date picker works in milliseconds since 1970 at midnight UTC. */
    fun toPickerMillis(date: LocalDate): Long = date.toEpochDays() * MILLIS_PER_DAY

    fun fromPickerMillis(millis: Long): LocalDate = LocalDate.fromEpochDays(millis.floorDiv(MILLIS_PER_DAY))
}
