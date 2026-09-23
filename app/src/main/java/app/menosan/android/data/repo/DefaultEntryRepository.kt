package app.menosan.android.data.repo

import app.menosan.android.core.model.Entry
import app.menosan.android.core.model.EntryDraft
import app.menosan.android.core.model.EntryRules
import app.menosan.android.core.model.EntrySyncStatus
import app.menosan.android.core.time.WeekCalc
import app.menosan.android.data.local.EntryDao
import app.menosan.android.data.local.EntryEntity
import app.menosan.android.data.local.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room part of [EntryRepository] (prep for the parallel workstreams). Writes go to Room with the right outbox state.
 * TODO(AN-1): SyncWorker, [requestSync], [refreshCurrentWeek], week rollover in [observeCurrentWeek], retention.
 */
@Singleton
class DefaultEntryRepository @Inject constructor(
    private val dao: EntryDao,
    private val taxonomy: TaxonomyRepository,
    private val clock: Clock,
) : EntryRepository {

    override fun observeWeek(weekStart: java.time.LocalDate): Flow<List<Entry>> =
        dao.observeWeek(weekStart).map { rows -> rows.map { it.toEntry() } }

    override fun observeCurrentWeek(): Flow<List<Entry>> = observeWeek(WeekCalc.currentWeekStart(clock))

    override suspend fun get(id: String): Entry? =
        dao.getById(id)?.takeIf { it.syncState != SyncState.PENDING_DELETE }?.toEntry()

    override suspend fun create(draft: EntryDraft): Entry {
        val clean = validate(draft)
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val entity = EntryEntity(
            id = UUID.randomUUID().toString(),
            name = clean.name,
            category = categoryOf(clean.subcategory),
            subcategory = clean.subcategory,
            quantity = clean.quantity,
            source = clean.source,
            createdAt = now,
            weekStart = WeekCalc.weekStart(now),
            updatedAt = now,
            syncState = SyncState.PENDING_CREATE,
        )
        dao.upsert(entity)
        requestSync()
        return entity.toEntry()
    }

    override suspend fun update(id: String, draft: EntryDraft): Entry {
        val existing = dao.getById(id)?.takeIf { it.syncState != SyncState.PENDING_DELETE }
            ?: throw EntryChangeException.NotFound()
        if (!WeekCalc.isCurrentWeek(existing.weekStart, clock)) throw EntryChangeException.WeekClosed()
        val clean = validate(draft)
        val updated = existing.copy(
            name = clean.name,
            category = categoryOf(clean.subcategory),
            subcategory = clean.subcategory,
            quantity = clean.quantity,
            updatedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS),
            // Never synced yet: still a create. Otherwise an update.
            syncState = if (existing.syncState == SyncState.PENDING_CREATE) SyncState.PENDING_CREATE else SyncState.PENDING_UPDATE,
            lastError = null,
        )
        dao.upsert(updated)
        requestSync()
        return updated.toEntry()
    }

    override suspend fun delete(id: String) {
        val existing = dao.getById(id) ?: throw EntryChangeException.NotFound()
        if (!WeekCalc.isCurrentWeek(existing.weekStart, clock)) throw EntryChangeException.WeekClosed()
        if (existing.syncState == SyncState.PENDING_CREATE) {
            dao.deleteById(id) // The server never saw it.
        } else {
            dao.upsert(existing.copy(syncState = SyncState.PENDING_DELETE, updatedAt = clock.instant(), lastError = null))
            requestSync()
        }
    }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override suspend fun hasPendingChanges(): Boolean = dao.getPending().isNotEmpty()

    override fun requestSync() {
        // TODO(AN-1): enqueue unique SyncWorker work (NetworkType.CONNECTED, exponential backoff).
    }

    override suspend fun refreshCurrentWeek() {
        // TODO(AN-1): GET /v1/entries and merge (server wins for SYNCED rows, local wins for pending ones).
    }

    private suspend fun validate(draft: EntryDraft): EntryDraft {
        val name = draft.name.trim()
        if (name.isEmpty() || name.length > EntryRules.NAME_MAX) throw EntryChangeException.Invalid("name")
        if (draft.quantity !in EntryRules.QUANTITY_MIN..EntryRules.QUANTITY_MAX) throw EntryChangeException.Invalid("quantity")
        if (taxonomy.taxonomy().subcategory(draft.subcategory) == null) throw EntryChangeException.Invalid("subcategory")
        return draft.copy(name = name)
    }

    private suspend fun categoryOf(subcategory: String) =
        taxonomy.taxonomy().subcategory(subcategory)?.category ?: throw EntryChangeException.Invalid("subcategory")

    private fun EntryEntity.toEntry() = Entry(
        id = id,
        name = name,
        category = category,
        subcategory = subcategory,
        quantity = quantity,
        source = source,
        createdAt = createdAt,
        weekStart = weekStart,
        syncStatus = when {
            syncState == SyncState.SYNCED -> EntrySyncStatus.SYNCED
            lastError != null -> EntrySyncStatus.FAILED
            else -> EntrySyncStatus.PENDING
        },
        lastError = lastError,
        editable = WeekCalc.isCurrentWeek(weekStart, clock),
    )
}
