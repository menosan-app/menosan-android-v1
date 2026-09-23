package app.menosan.android.di

import app.menosan.android.data.repo.DefaultEntryRepository
import app.menosan.android.data.repo.EntryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Repository interface bindings. Workstreams add their own Hilt modules in their own packages
 * (e.g. `data/repo/ReportModule.kt` for AN-3) instead of editing this file.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun entryRepository(impl: DefaultEntryRepository): EntryRepository
}
