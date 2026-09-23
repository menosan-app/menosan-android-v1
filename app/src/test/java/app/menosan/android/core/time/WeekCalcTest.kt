package app.menosan.android.core.time

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class WeekCalcTest {
    private lateinit var originalZone: TimeZone

    // Week math must not depend on the device zone (plan §4), so run every test from a far-away default zone.
    @Before
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(originalZone)

    private fun at(iso: String) = Instant.parse(iso)
    private fun clockAt(iso: String): Clock = Clock.fixed(at(iso), ZoneOffset.UTC)

    @Test
    fun `Saturday 23-59-59 PHT belongs to the week that is ending`() {
        // Sat 2026-10-03 23:59:59.999 PHT == 15:59:59.999 UTC.
        assertEquals(LocalDate.of(2026, 9, 27), WeekCalc.weekStart(at("2026-10-03T15:59:59.999Z")))
    }

    @Test
    fun `Sunday 00-00 PHT starts the next week`() {
        // Sun 2026-10-04 00:00 PHT == Sat 16:00 UTC.
        assertEquals(LocalDate.of(2026, 10, 4), WeekCalc.weekStart(at("2026-10-03T16:00:00Z")))
    }

    @Test
    fun `a Saturday afternoon in UTC can already be Sunday in Manila`() {
        assertEquals(LocalDate.of(2026, 10, 4), WeekCalc.weekStart(at("2026-10-03T20:00:00Z")))
    }

    @Test
    fun `mid-week instants map to that week's Sunday`() {
        assertEquals(LocalDate.of(2026, 9, 27), WeekCalc.weekStart(at("2026-09-30T04:00:00Z")))
    }

    @Test
    fun `week end is the Saturday six days later`() {
        assertEquals(LocalDate.of(2026, 10, 3), WeekCalc.weekEnd(LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun `start and end instants are Manila midnights`() {
        val week = LocalDate.of(2026, 9, 27)
        assertEquals(at("2026-09-26T16:00:00Z"), WeekCalc.startInstant(week))
        assertEquals(at("2026-10-03T16:00:00Z"), WeekCalc.endExclusive(week))
    }

    @Test
    fun `a week closes exactly at the next Sunday 00-00 PHT`() {
        val week = LocalDate.of(2026, 9, 27)
        assertFalse(WeekCalc.isClosed(week, clockAt("2026-10-03T15:59:59.999Z")))
        assertTrue(WeekCalc.isClosed(week, clockAt("2026-10-03T16:00:00Z")))
    }

    @Test
    fun `current week and latest report week`() {
        val clock = clockAt("2026-09-30T04:00:00Z")
        assertEquals(LocalDate.of(2026, 9, 27), WeekCalc.currentWeekStart(clock))
        assertTrue(WeekCalc.isCurrentWeek(LocalDate.of(2026, 9, 27), clock))
        assertFalse(WeekCalc.isCurrentWeek(LocalDate.of(2026, 9, 20), clock))
        assertEquals(LocalDate.of(2026, 9, 20), WeekCalc.latestReportWeekStart(clock))
    }

    @Test
    fun `only Sundays are week starts`() {
        assertTrue(WeekCalc.isWeekStart(LocalDate.of(2026, 9, 27)))
        assertFalse(WeekCalc.isWeekStart(LocalDate.of(2026, 9, 26)))
    }
}
