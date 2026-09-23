package app.menosan.android.feature.entries

import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.EntrySource
import app.menosan.android.core.model.EntrySyncStatus
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.time.WeekCalc
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EntryFormatsTest {
    private val week = LocalDate.parse("2026-09-27")

    @Test
    fun `dates and times are shown in Manila`() {
        // Sat 2026-10-03 16:30 UTC is Sun 2026-10-04 00:30 PHT.
        val instant = Instant.parse("2026-10-03T16:30:00Z")
        assertEquals("Oct 4, 2026", EntryFormats.date(instant))
        assertEquals("12:30 am", EntryFormats.time(instant))
        assertEquals("Oct 4 · 12:30 am", EntryFormats.shortDateTime(instant))
        assertEquals(0, EntryFormats.dayIndex(instant))
    }

    @Test
    fun `week range and editable-until use the Saturday that ends the week`() {
        assertEquals("Sep 27 – Oct 3", EntryFormats.weekRange(week))
        assertEquals("Sat, Oct 3, 11:59 pm", EntryFormats.editableUntil(week))
    }

    @Test
    fun `week summary counts entries and pieces per day and category`() {
        val entries = listOf(
            entry("2026-09-27T01:00:00Z", WasteCategory.RESIDUAL, 3), // Sun
            entry("2026-09-29T02:00:00Z", WasteCategory.RESIDUAL, 2), // Tue
            entry("2026-09-29T03:00:00Z", WasteCategory.SPECIAL, 1), // Tue
            entry("2026-10-03T15:59:59Z", WasteCategory.BIODEGRADABLE, 5), // Sat 23:59:59 PHT
        )
        val summary = WeekSummary.of(entries)
        assertEquals(4, summary.entries)
        assertEquals(11, summary.pieces)
        assertEquals(listOf(1, 0, 2, 0, 0, 0, 1), summary.entriesPerDay)
        assertEquals(CategoryTotal(2, 5), summary.byCategory[WasteCategory.RESIDUAL])
        assertEquals(CategoryTotal(1, 1), summary.byCategory[WasteCategory.SPECIAL])
        assertEquals(CategoryTotal(0, 0), summary.byCategory[WasteCategory.RECYCLABLE])
    }

    private fun entry(at: String, category: WasteCategory, quantity: Int): Entry {
        val createdAt = Instant.parse(at)
        return Entry(
            id = at, name = "Item", category = category, subcategory = "X", quantity = quantity, source = EntrySource.MANUAL,
            createdAt = createdAt, weekStart = WeekCalc.weekStart(createdAt), syncStatus = EntrySyncStatus.SYNCED, editable = true,
        )
    }
}
