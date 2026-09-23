package app.menosan.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** WorkManager is initialised on demand with Hilt's factory, so `@HiltWorker` workers (AN-1's SyncWorker) get injected. */
@HiltAndroidApp
class MenosanApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
