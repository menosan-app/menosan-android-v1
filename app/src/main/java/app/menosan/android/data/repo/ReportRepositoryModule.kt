package app.menosan.android.data.repo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** AN-3's Hilt bindings (kept out of `di/` so parallel workstreams don't edit the same file). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReportRepositoryModule {
    @Binds
    abstract fun reportRepository(impl: DefaultReportRepository): ReportRepository

    @Binds
    abstract fun reportEntrySource(impl: RoomReportEntrySource): ReportEntrySource
}
