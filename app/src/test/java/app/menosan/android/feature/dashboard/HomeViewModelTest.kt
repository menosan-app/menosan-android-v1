package app.menosan.android.feature.dashboard

import androidx.lifecycle.viewModelScope
import app.menosan.android.R
import app.menosan.android.core.model.EntrySyncStatus
import app.menosan.android.data.remote.dto.ImpactDto
import app.menosan.android.data.remote.dto.ReportDto
import app.menosan.android.data.remote.dto.Trend
import app.menosan.android.data.repo.ReportFixtures
import app.menosan.android.data.repo.ReportView
import app.menosan.android.feature.settings.FakeAuth
import app.menosan.android.feature.settings.FakeEntries
import app.menosan.android.feature.settings.FakeNetwork
import app.menosan.android.feature.settings.FakeReports
import app.menosan.android.feature.settings.testEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    // Tue 2026-10-06 10:00 PHT: the current week starts Sun 10-04, and the week that just ended is 09-27.
    private val clock = ReportFixtures.CLOCK
    private val week = ReportFixtures.CURRENT_WEEK
    private val lastWeek = ReportFixtures.LATEST_WEEK

    private val entries = FakeEntries()
    private val reports = FakeReports()
    private val network = FakeNetwork()
    private val labels = SubcategoryLabels { mapOf("RES_SACHETS" to "Sachets & small packets") }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Runs [block] with a subscribed view model. `runCurrent`, not `advanceUntilIdle`: the week flow ticks forever. */
    private fun home(block: suspend TestScope.(HomeViewModel) -> Unit) = runTest {
        val vm = HomeViewModel(entries, reports, labels, network, FakeAuth(), clock)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        try {
            block(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun at(date: LocalDate, hour: Int): Instant = date.atTime(hour, 0).atZone(java.time.ZoneOffset.ofHours(8)).toInstant()

    private fun view(report: ReportDto, canAdopt: Boolean, provisional: Boolean = false) = ReportView(
        report = report,
        isProvisional = provisional,
        canAdopt = canAdopt,
        recap = null,
        missingComparisons = false,
        followupNotMeasured = false,
        savedAt = ReportFixtures.NOW,
    )

    @Test
    fun `a new user gets the welcome state and a refresh on open`() = home { vm ->
        val state = vm.state.value
        assertFalse(state.loading)
        assertTrue(state.isNewUser)
        assertNull(state.latestReport)
        assertEquals(0, state.summary.entries)
        assertEquals("Liza", state.firstName)
        assertEquals(week, state.weekStart)
        assertEquals(1, reports.refreshes)
        assertEquals(1, entries.refreshes)
    }

    @Test
    fun `this week's live totals, recent entries, and sync counts`() = home { vm ->
        entries.entries.value = listOf(
            testEntry("a", at(week, 9), week, quantity = 2),
            testEntry("b", at(week.plusDays(2), 8), week, quantity = 5, status = EntrySyncStatus.PENDING),
            testEntry("c", at(week.plusDays(1), 12), week, quantity = 1, status = EntrySyncStatus.FAILED),
            testEntry("d", at(week.plusDays(2), 7), week, quantity = 3),
            // A row from last week (e.g. right at the rollover) never counts toward this week.
            testEntry("old", at(lastWeek, 9), lastWeek, quantity = 9),
        )
        entries.pending.value = 1
        runCurrent()

        val state = vm.state.value
        assertEquals(4, state.summary.entries)
        assertEquals(11, state.summary.pieces)
        assertEquals(listOf(1, 1, 2, 0, 0, 0, 0), state.summary.entriesPerDay)
        assertEquals(listOf("b", "d", "c"), state.recentEntries.map { it.id })
        assertEquals(1, state.pendingCount)
        assertEquals(1, state.failedCount)
        assertFalse(state.isNewUser)
        assertEquals("Sachets & small packets", state.labelOf("RES_SACHETS"))
        assertEquals("BIO_OTHER", state.labelOf("BIO_OTHER"))
    }

    @Test
    fun `last week's report shows its top hotspot and what you're trying`() = home { vm ->
        val report = ReportFixtures.serverReport(
            lastWeek,
            hotspots = listOf("RES_SACHETS" to 40, "RES_PLASTIC_BAGS" to 12),
            recommendations = mapOf(
                "RES_SACHETS" to listOf(ReportFixtures.recommendation("r1", adopted = true, title = "Refill station")),
                "RES_PLASTIC_BAGS" to listOf(ReportFixtures.recommendation("r2", title = "Bring a bayong")),
            ),
        )
        reports.latest.value = view(report, canAdopt = true)
        runCurrent()

        val card = vm.state.value.latestReport!!
        assertTrue(card.isLastWeek)
        assertFalse(card.isProvisional)
        assertEquals(2, card.analyzedEntries)
        assertEquals(52, card.analyzedPieces)
        assertEquals("RES_SACHETS", card.topHotspot!!.subcategory)
        assertEquals(listOf("Refill station"), card.trying)
        assertFalse(card.hasIdeasToPick)
        assertFalse(vm.state.value.isNewUser)
    }

    @Test
    fun `adoptions are only shown while the report still accepts them`() = home { vm ->
        val report = ReportFixtures.serverReport(
            lastWeek,
            hotspots = listOf("RES_SACHETS" to 4),
            recommendations = mapOf("RES_SACHETS" to listOf(ReportFixtures.recommendation("r1", adopted = true))),
        )
        reports.latest.value = view(report, canAdopt = false)
        runCurrent()

        val card = vm.state.value.latestReport!!
        assertTrue(card.trying.isEmpty())
        assertFalse(card.hasIdeasToPick)
    }

    @Test
    fun `a latest report with ideas but no pick nudges gently`() = home { vm ->
        val report = ReportFixtures.serverReport(
            lastWeek,
            hotspots = listOf("RES_SACHETS" to 4),
            recommendations = mapOf("RES_SACHETS" to listOf(ReportFixtures.recommendation("r1"))),
        )
        reports.latest.value = view(report, canAdopt = true)
        runCurrent()

        assertTrue(vm.state.value.latestReport!!.hasIdeasToPick)
    }

    @Test
    fun `an older offline summary is labeled as such and carries its impacts`() = home { vm ->
        val older = LocalDate.of(2026, 9, 20)
        val impact = ImpactDto("r9", "Bring a bayong", "RES_PLASTIC_BAGS", LocalDate.of(2026, 9, 13), 15, 9, Trend.DECREASED)
        val report = ReportFixtures.serverReport(older, hotspots = listOf("RES_SACHETS" to 3), isLatest = false)
            .copy(impacts = listOf(impact))
        reports.latest.value = view(report, canAdopt = false, provisional = true)
        runCurrent()

        val card = vm.state.value.latestReport!!
        assertTrue(card.isProvisional)
        assertFalse(card.isLastWeek)
        assertEquals(older, card.weekStart)
        assertEquals(listOf(impact), card.impacts)
    }

    @Test
    fun `deleting from Home goes through the repository and confirms`() = home { vm ->
        entries.entries.value = listOf(testEntry("a", at(week, 9), week))
        val messages = mutableListOf<Int>()
        backgroundScope.launch { vm.messageEvents.toList(messages) }
        runCurrent()

        vm.delete("a")
        runCurrent()

        assertEquals(listOf("a"), entries.deleted)
        assertEquals(listOf(R.string.delete_done), messages)
        assertTrue(vm.state.value.recentEntries.isEmpty())
    }

    @Test
    fun `connectivity changes update the header pill`() = home { vm ->
        assertTrue(vm.state.value.online)
        network.state.value = false
        runCurrent()
        assertFalse(vm.state.value.online)
    }
}
