package app.menosan.android.feature.entries

import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.time.WeekCalc
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date text and week totals for the Audit screens. Always in Asia/Manila (plan §4), and English (plan §2.1).
 * Pure functions, so they're unit-tested.
 */
object EntryFormats {
    private val dayMonth = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val fullDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val weekday = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

    /** "Sep 27 – Oct 3" */
    fun weekRange(weekStart: LocalDate): String =
        "${dayMonth.format(weekStart)} – ${dayMonth.format(WeekCalc.weekEnd(weekStart))}"

    /** "Sep 29, 2026" */
    fun date(instant: Instant): String = fullDate.format(instant.atZone(WeekCalc.ZONE))

    /** "10:24 am" */
    fun time(instant: Instant): String = time.format(instant.atZone(WeekCalc.ZONE)).lowercase(Locale.ENGLISH)

    /** "Sep 29 · 10:24 am", for entry rows. */
    fun shortDateTime(instant: Instant): String =
        "${dayMonth.format(instant.atZone(WeekCalc.ZONE))} · ${time(instant)}"

    /** "Sat, Oct 3, 11:59 pm": the end of the entry's logging week, when editing closes (SFR11). */
    fun editableUntil(weekStart: LocalDate): String = "${weekday.format(WeekCalc.weekEnd(weekStart))}, 11:59 pm"

    /** Sunday = 0 … Saturday = 6, in Manila. */
    fun dayIndex(instant: Instant): Int = instant.atZone(WeekCalc.ZONE).dayOfWeek.let { if (it == DayOfWeek.SUNDAY) 0 else it.value }

    fun dayIndex(date: LocalDate): Int = if (date.dayOfWeek == DayOfWeek.SUNDAY) 0 else date.dayOfWeek.value
}

data class CategoryTotal(val entries: Int = 0, val pieces: Int = 0)

/**
 * Live totals of the week in progress: entries (frequency) and pieces (quantity), per day and per main category.
 * No comparisons or hotspots here; those only exist in reports for closed weeks (DESIGN.md §5.4).
 */
data class WeekSummary(
    val entries: Int,
    val pieces: Int,
    /** Entries per day, Sunday first. */
    val entriesPerDay: List<Int>,
    val byCategory: Map<WasteCategory, CategoryTotal>,
) {
    companion object {
        fun of(entries: List<Entry>): WeekSummary {
            val perDay = IntArray(7)
            entries.forEach { perDay[EntryFormats.dayIndex(it.createdAt)]++ }
            return WeekSummary(
                entries = entries.size,
                pieces = entries.sumOf { it.quantity },
                entriesPerDay = perDay.toList(),
                byCategory = WasteCategory.entries.associateWith { category ->
                    entries.filter { it.category == category }.let { CategoryTotal(it.size, it.sumOf { e -> e.quantity }) }
                },
            )
        }
    }
}
