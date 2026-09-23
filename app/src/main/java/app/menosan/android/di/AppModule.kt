package app.menosan.android.di

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.room.Room
import app.menosan.android.core.auth.AuthService
import app.menosan.android.core.auth.FirebaseAuthService
import app.menosan.android.core.auth.IdTokenProvider
import app.menosan.android.data.local.EntryDao
import app.menosan.android.data.local.MenosanDatabase
import app.menosan.android.data.local.ReportCacheDao
import app.menosan.android.data.local.TaxonomyDao
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Inject this instead of calling `Instant.now()`, so tests can control time (plan §4). */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun credentialManager(@ApplicationContext context: Context): CredentialManager = CredentialManager.create(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindings {
    @Binds
    abstract fun authService(impl: FirebaseAuthService): AuthService

    @Binds
    abstract fun idTokenProvider(impl: FirebaseAuthService): IdTokenProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** No destructive fallback, ever: pending entries must survive app updates (NFR7). */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MenosanDatabase =
        Room.databaseBuilder(context, MenosanDatabase::class.java, MenosanDatabase.NAME)
            .addMigrations(*MenosanDatabase.MIGRATIONS)
            .build()

    @Provides
    fun entryDao(db: MenosanDatabase): EntryDao = db.entryDao()

    @Provides
    fun reportCacheDao(db: MenosanDatabase): ReportCacheDao = db.reportCacheDao()

    @Provides
    fun taxonomyDao(db: MenosanDatabase): TaxonomyDao = db.taxonomyDao()
}
