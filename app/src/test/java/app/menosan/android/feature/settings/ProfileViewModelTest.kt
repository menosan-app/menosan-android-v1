package app.menosan.android.feature.settings

import app.menosan.android.R
import app.menosan.android.core.network.ApiErrorCode
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    // Thu 2026-09-24 17:30 UTC = Fri 2026-09-25 01:30 in Manila.
    private val clock = Clock.fixed(Instant.parse("2026-09-24T17:30:00Z"), ZoneOffset.UTC)
    private val entries = FakeEntries()
    private val network = FakeNetwork()
    private val appearance = FakeAppearance()
    private val actions = FakeAccountActions()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): Pair<ProfileViewModel, List<ProfileEvent>> {
        val vm = ProfileViewModel(FakeAuth(), entries, appearance, network, actions, clock)
        val events = mutableListOf<ProfileEvent>()
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.eventFlow.toList(events) }
        return vm to events
    }

    private fun ProfileViewModel.deleteDialog() = state.value.dialog as? ProfileDialog.DeleteAccount

    // Helpers

    @Test
    fun `export file name uses the Manila date, not UTC`() {
        assertEquals("menosan-export-2026-09-25.json", exportFileName(clock))
        assertEquals(
            "menosan-export-2026-09-24.json",
            exportFileName(Clock.fixed(Instant.parse("2026-09-24T15:59:59Z"), ZoneOffset.UTC)),
        )
    }

    @Test
    fun `initials come from the name, else the email`() {
        assertEquals("LC", initialsOf("Liza Cabaraban", "liza@example.com"))
        assertEquals("LC", initialsOf("  liza  de la  cabaraban ", null))
        assertEquals("L", initialsOf("Liza", null))
        assertEquals("M", initialsOf(null, "menosan.tester@example.com"))
        assertEquals("", initialsOf(" ", null))
    }

    @Test
    fun `only the word DELETE confirms`() {
        assertTrue(isDeleteConfirmation("DELETE"))
        assertTrue(isDeleteConfirmation("  delete "))
        assertFalse(isDeleteConfirmation("DELET"))
        assertFalse(isDeleteConfirmation("DELETE!"))
        assertFalse(isDeleteConfirmation(""))
    }

    // State

    @Test
    fun `state shows the account, appearance, and pending count`() = runTest {
        entries.pending.value = 2
        val (vm, _) = viewModel()
        vm.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Liza Cabaraban", state.displayName)
        assertEquals("liza@example.com", state.email)
        assertEquals("LC", state.initials)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(2, state.pendingCount)
    }

    // Logout (UFR3)

    @Test
    fun `logout with an empty outbox clears local data and signs out`() = runTest {
        val (vm, events) = viewModel()
        vm.logOut()
        advanceUntilIdle()

        assertEquals(listOf("endSession"), actions.calls)
        assertEquals(listOf(ProfileEvent.LoggedOut), events)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `logout with pending entries warns first and keeps everything`() = runTest {
        entries.pending.value = 3
        val (vm, _) = viewModel()
        vm.logOut()
        advanceUntilIdle()

        assertEquals(ProfileDialog.LogoutWarning(3), vm.state.value.dialog)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun `try to sync first requests a sync and stays signed in`() = runTest {
        entries.pending.value = 1
        val (vm, events) = viewModel()
        vm.logOut()
        advanceUntilIdle()
        vm.syncFirst()
        advanceUntilIdle()

        assertEquals(1, entries.syncRequests)
        assertTrue(actions.calls.isEmpty())
        assertNull(vm.state.value.dialog)
        assertEquals(listOf(ProfileEvent.Message(R.string.profile_logout_syncing)), events)
    }

    @Test
    fun `log out anyway ends the session despite pending entries`() = runTest {
        entries.pending.value = 1
        val (vm, events) = viewModel()
        vm.logOut()
        advanceUntilIdle()
        vm.logOutAnyway()
        advanceUntilIdle()

        assertEquals(listOf("endSession"), actions.calls)
        assertEquals(listOf(ProfileEvent.LoggedOut), events)
    }

    @Test
    fun `a failed logout shows a message and can be retried`() = runTest {
        actions.endSessionFails = true
        val (vm, events) = viewModel()
        vm.logOut()
        advanceUntilIdle()

        assertFalse(vm.state.value.loggingOut)
        assertEquals(listOf(ProfileEvent.Message(R.string.profile_logout_failed)), events)
    }

    // Account deletion (UFR4, NFR4)

    @Test
    fun `delete is enabled only after typing DELETE`() = runTest {
        val (vm, _) = viewModel()
        vm.showDialog(ProfileDialog.DeleteAccount())
        vm.onDeleteTextChange("DEL")
        vm.confirmDelete()
        advanceUntilIdle()

        assertFalse(vm.deleteDialog()!!.canDelete)
        assertTrue(actions.calls.isEmpty())

        vm.onDeleteTextChange("DELETE")
        runCurrent()
        assertTrue(vm.deleteDialog()!!.canDelete)
    }

    @Test
    fun `204 clears local data, signs out, and says goodbye`() = runTest {
        val (vm, events) = viewModel()
        vm.showDialog(ProfileDialog.DeleteAccount())
        vm.onDeleteTextChange("DELETE")
        vm.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("delete", "endSession"), actions.calls)
        assertEquals(listOf(ProfileEvent.AccountDeleted), events)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `500 is retried and then succeeds`() = runTest {
        actions.deleteResults = listOf(httpError(500), ApiResult.Success(Unit, 204))
        val (vm, events) = viewModel()
        vm.showDialog(ProfileDialog.DeleteAccount())
        vm.onDeleteTextChange("delete")
        vm.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("delete", "delete", "endSession"), actions.calls)
        assertEquals(listOf(ProfileEvent.AccountDeleted), events)
    }

    @Test
    fun `repeated 500 stops after a few tries, keeps the account, and allows another try`() = runTest {
        actions.deleteResults = listOf(httpError(500))
        val (vm, events) = viewModel()
        vm.showDialog(ProfileDialog.DeleteAccount())
        vm.onDeleteTextChange("DELETE")
        vm.confirmDelete()
        advanceUntilIdle()

        assertEquals(List(ProfileViewModel.DELETE_ATTEMPTS) { "delete" }, actions.calls)
        val dialog = vm.deleteDialog()!!
        assertEquals(DeleteError.SERVER, dialog.error)
        assertFalse(dialog.deleting)
        assertTrue(dialog.canDelete)
        assertTrue(events.isEmpty())

        actions.deleteResults = listOf(ApiResult.Success(Unit, 204))
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals("endSession", actions.calls.last())
        assertEquals(listOf(ProfileEvent.AccountDeleted), events)
    }

    @Test
    fun `offline delete doesn't call the server`() = runTest {
        network.state.value = false
        val (vm, _) = viewModel()
        vm.showDialog(ProfileDialog.DeleteAccount())
        vm.onDeleteTextChange("DELETE")
        vm.confirmDelete()
        advanceUntilIdle()

        assertEquals(DeleteError.OFFLINE, vm.deleteDialog()!!.error)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun `401 is not retried and asks to sign in again`() = runTest {
        actions.deleteResults = listOf(httpError(401, ApiErrorCode.UNAUTHENTICATED))
        val (vm, _) = viewModel()
        vm.showDialog(ProfileDialog.DeleteAccount())
        vm.onDeleteTextChange("DELETE")
        vm.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf("delete"), actions.calls)
        assertEquals(DeleteError.SIGN_IN_EXPIRED, vm.deleteDialog()!!.error)
    }

    @Test
    fun `a delete in progress can't be dismissed`() = runTest {
        actions.deleteResults = listOf(httpError(500))
        val (vm, _) = viewModel()
        vm.showDialog(ProfileDialog.DeleteAccount())
        vm.onDeleteTextChange("DELETE")
        vm.confirmDelete()
        runCurrent() // The first try fails and waits before retrying.
        vm.dismissDialog()
        runCurrent()

        assertTrue(vm.deleteDialog()!!.deleting)
        advanceUntilIdle()
        vm.dismissDialog()
        runCurrent()
        assertNull(vm.state.value.dialog)
    }

    // Export (NFR16)

    @Test
    fun `export opens the file picker with the Manila-dated name, then streams to the picked file`() = runTest {
        val (vm, events) = viewModel()
        vm.showDialog(ProfileDialog.ConfirmExport)
        vm.confirmExport()
        advanceUntilIdle()
        assertEquals(listOf(ProfileEvent.PickExportFile("menosan-export-2026-09-25.json")), events)
        assertNull(vm.state.value.dialog)

        vm.onExportDestination("content://docs/menosan-export-2026-09-25.json")
        advanceUntilIdle()
        assertEquals(listOf("content://docs/menosan-export-2026-09-25.json"), actions.exportedTo)
        assertEquals(ProfileEvent.Message(R.string.profile_export_done), events.last())
        assertFalse(vm.state.value.exporting)
    }

    @Test
    fun `export offline or cancelled does nothing but explain`() = runTest {
        network.state.value = false
        val (vm, events) = viewModel()
        vm.confirmExport()
        vm.onExportDestination(null)
        advanceUntilIdle()

        assertEquals(listOf(ProfileEvent.Message(R.string.profile_export_offline)), events)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun `export failures map to friendly messages`() = runTest {
        actions.exportResult = ExportResult.WRITE_FAILED
        val (vm, events) = viewModel()
        vm.onExportDestination("content://docs/x.json")
        advanceUntilIdle()

        assertEquals(listOf(ProfileEvent.Message(R.string.profile_export_write_failed)), events)
    }
}
