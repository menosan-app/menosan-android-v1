package app.menosan.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.menosan.android.sync.SyncTriggers
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** WorkManager is initialised on demand with Hilt's factory, so `@HiltWorker` workers (AN-1's SyncWorker) get injected. */
@HiltAndroidApp
class MenosanApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncTriggers: SyncTriggers

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        syncTriggers.start() // AN-1: sync on app start and sign-in, merge the current week when online.
    }
}
