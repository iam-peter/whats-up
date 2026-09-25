package app.whatsup.config

import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import kotlinx.serialization.Serializable

enum class Density { COMPACT, COMFORTABLE }
enum class BackgroundStyle { PER_CELL, SINGLE }
enum class WeekendStyle { NONE, TINTED }

@Serializable
data class GlobalConfig(
    /** null = all visible calendars. */
    val calendarIds: Set<Long>? = null,
    /** null = holiday calendar with the lowest ID (spec D-4). */
    val preferredHolidayCalendarId: Long? = null,
    /** Calendars the user marked as holiday calendars, besides Google's (FR-D5). */
    val extraHolidayCalendarIds: Set<Long> = emptySet(),
    val birthdaysFromContacts: Boolean = true,
    val birthdaysFromCalendar: Boolean = true,
    /** Fill per calendar ID; calendars without an entry are solid. */
    val calendarPatterns: Map<Long, ChipPattern> = emptyMap(),
    /** Outline per calendar ID; calendars without an entry are solid. */
    val calendarLineStyles: Map<Long, LineStyle> = emptyMap(),
    /** Contact birthdays belong to no calendar, so they have their own setting. */
    val contactBirthdayPattern: ChipPattern = ChipPattern.NONE,
)

@Serializable
data class WidgetConfig(
    /** null = use [GlobalConfig.calendarIds]. */
    val calendarIds: Set<Long>? = null,
    /** null = derived from widget height. */
    val weeks: Int? = null,
    val textScale: Float = 1f,
    val density: Density = Density.COMPACT,
    val background: BackgroundStyle = BackgroundStyle.PER_CELL,
    val backgroundOpacity: Float = 1f,
    val weekendStyle: WeekendStyle = WeekendStyle.NONE,
    val showWeekNumbers: Boolean = false,
    /** Cake icon on birthday chips: always or never (FR-B6). */
    val showBirthdayIcon: Boolean = true,
    val dynamicColors: Boolean = true,
    /** Accent (today border, day number) when [dynamicColors] is off; ARGB. */
    val accentColor: Int = 0xFF3F51B5.toInt(),
    /** Start time in front of timed events (FR-E3). */
    val showEventTimes: Boolean = true,
    /** null = system default handler for calendar intents. */
    val calendarPackage: String? = null,
)

@Serializable
data class AppConfig(
    val global: GlobalConfig = GlobalConfig(),
    val widgets: Map<Int, WidgetConfig> = emptyMap(),
    /** Bumped to make running widget sessions reload their data. */
    val refreshToken: Long = 0,
) {
    fun widget(appWidgetId: Int) = widgets[appWidgetId] ?: WidgetConfig()
    fun calendarIdsFor(appWidgetId: Int) = widget(appWidgetId).calendarIds ?: global.calendarIds
}
