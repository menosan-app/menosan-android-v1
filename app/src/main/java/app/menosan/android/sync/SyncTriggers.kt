package app.menosan.android.sync

import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.network.ConnectivityObserver
import app.menosan.android.data.repo.EntryRepository
import app.menosan.android.data.repo.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level sync triggers (plan §10 AN-1), started once from `MenosanApp.onCreate`:
 * - on app start, prune entries older than the retention window;
 * - on app start and after every sign-in, request a sync of the outbox;
 * - whenever a signed-in user is (or comes back) online, pull the current week and merge it;
 * - for a signed-in user, refresh reports (offline, this builds provisional summaries for closed weeks).
 * Saves request their own sync through the repository.
 */
@Singleton
class SyncTriggers @Inject constructor(
    private val auth: AuthService,
    private val connectivity: ConnectivityObserver,
    private val repository: EntryRepository,
    private val engine: EntrySyncEngine,
    private val reports: ReportRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { runCatching { engine.pruneOldEntries() } }
        scope.launch {
            val signedIn = auth.authState.map { it?.uid }.distinctUntilChanged()
            var lastUid: String? = null
            combine(signedIn, connectivity.online) { uid, online -> uid to online }.collectLatest { (uid, online) ->
                if (uid == null) {
                    lastUid = null
                    return@collectLatest
                }
                if (uid != lastUid) {
                    lastUid = uid
                    repository.requestSync()
                }
                if (online) repository.refreshCurrentWeek()
                // Integration (AN-3): server reports when online; offline summaries for closed weeks otherwise (plan §5.7).
                runCatching { reports.refreshReports() }
            }
        }
    }
}
