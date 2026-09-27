package app.whatsup.config

import app.whatsup.logic.GooglePalette
import app.whatsup.model.ChipPattern
import app.whatsup.model.LineStyle
import kotlinx.serialization.Serializable

enum class Density { COMPACT, COMFORTABLE }
enum class BackgroundStyle { PER_CELL, SINGLE }
enum class WeekendStyle { NONE, TINTED }

/** What a widget shows (spec section 4.1). */
enum class WidgetLayout {
    ROLLING_GRID, MONTH_GRID, AGENDA, BIRTHDAYS, NEXT_UP,
    /** M3: time axis. */
    WEEK_TIMELINE, DAY_TIMELINE,
    /** M3: two blocks in one widget. */
    GRID_AGENDA, NEXT_UP_BIRTHDAYS,
}

/** Pink, as Chronos uses for birthdays. */
const val CONTACT_BIRTHDAY_COLOR = 0xFFE91E63.toInt()

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
    /** Birthdays from Contacts (FR-B7). */
    val contactBirthdayColor: Int = CONTACT_BIRTHDAY_COLOR,
    /** Google Calendar's birthdays; its birthday green, as in the Google Calendar app (FR-B7). */
    val googleBirthdayColor: Int = GooglePalette.BIRTHDAY,
    /** Fill per calendar ID; calendars without an entry are solid. */
    val calendarPatterns: Map<Long, ChipPattern> = emptyMap(),
    /**
     * Colour per calendar ID, replacing the source colour (FR-E4a). Android
     * doesn't receive colour changes made in Google Calendar, so this lets
     * the widget match what Google Calendar shows. Absent = source colour.
     */
    val calendarColors: Map<Long, Int> = emptyMap(),
    /** Outline per calendar ID; calendars without an entry are solid. */
    val calendarLineStyles: Map<Long, LineStyle> = emptyMap(),
    /** Birthdays have their own patterns, per source, instead of their calendar's. */
    val contactBirthdayPattern: ChipPattern = ChipPattern.NONE,
    val googleBirthdayPattern: ChipPattern = ChipPattern.NONE,
)

@Serializable
data class WidgetConfig(
    /** null = use [GlobalConfig.calendarIds]. */
    val calendarIds: Set<Long>? = null,
    val layout: WidgetLayout = WidgetLayout.ROLLING_GRID,
    /** Days ahead for the agenda, birthday and next-up layouts. */
    val lookAheadDays: Int = 14,
    /** null = derived from widget height (rolling grid). */
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
    /** Day popup opens next to the tapped day (like Chronos) instead of centred (FR-I2). */
    val popupNextToDay: Boolean = true,
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
