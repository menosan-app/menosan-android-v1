package app.menosan.android.data.repo

import app.menosan.android.sync.AfterSyncAction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Integration of AN-1 sync and AN-3 reports (plan §5.7): after the outbox is flushed, fetch server reports so they
 * replace provisional offline summaries and pick up weeks that changed because of a late sync.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReportSyncHooks {
    @Provides
    @IntoSet
    fun refreshReportsAfterSync(reports: ReportRepository): AfterSyncAction =
        AfterSyncAction { reports.refreshAfterSync() }
}
