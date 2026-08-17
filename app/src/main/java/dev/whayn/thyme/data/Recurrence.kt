package dev.whayn.thyme.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Day-of-week bitmask helpers and human-readable summaries for [Regimen].
 *
 * The mask uses `1 shl (DayOfWeek.value - 1)`, so Monday is bit 0 and Sunday
 * bit 6. A bitmask rather than a child table because SQLite can filter it
 * directly with `&`, keeping the whole recurrence test inside one query.
 */
object Recurrence {

    const val EVERY_DAY = 0b111_1111   // 127
    const val WEEKDAYS = 0b001_1111    // Mon–Fri
    const val WEEKENDS = 0b110_0000    // Sat–Sun

    private val summaryDateFormat = DateTimeFormatter.ofPattern("d MMM")

    fun bitOf(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun bitOf(date: LocalDate): Int = date.dayOfWeekBit()

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bitOf(day) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bitOf(day)

    /** Monday-first, matching how the editor lays the day toggles out. */
    val orderedDays: List<DayOfWeek> = DayOfWeek.entries.toList()

    private fun shortName(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    /** The recurrence rule alone, e.g. "Weekdays" or "21 on / 7 off". */
    fun describeRule(regimen: Regimen): String {
        val on = regimen.cycleOnDays
        val off = regimen.cycleOffDays
        if (on != null && off != null) return "$on on / $off off"

        if (regimen.intervalDays > 1) return "Every ${regimen.intervalDays} days"

        return when (regimen.daysOfWeek) {
            EVERY_DAY -> "Every day"
            WEEKDAYS -> "Weekdays"
            WEEKENDS -> "Weekends"
            else -> orderedDays
                .filter { contains(regimen.daysOfWeek, it) }
                .joinToString(", ") { shortName(it) }
                .ifEmpty { "No days selected" }
        }
    }

    /** Rule plus validity window, e.g. "Weekdays · until 23 Aug". */
    fun summarise(regimen: Regimen, today: LocalDate = LocalDate.now()): String {
        val parts = buildList {
            add(describeRule(regimen))
            if (regimen.startDate.isAfter(today)) {
                add("from ${regimen.startDate.format(summaryDateFormat)}")
            }
            regimen.endDate?.let { end ->
                add(
                    if (end.isBefore(today)) "ended ${end.format(summaryDateFormat)}"
                    else "until ${end.format(summaryDateFormat)}"
                )
            }
        }
        return parts.joinToString(" · ")
    }
}

/** The SQLite query's day mask for this date. Monday is bit zero. */
fun LocalDate.dayOfWeekBit(): Int = 1 shl (dayOfWeek.value - 1)
