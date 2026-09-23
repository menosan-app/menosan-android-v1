package app.menosan.android.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.menosan.android.core.network.ApiError
import app.menosan.android.core.network.ApiResult
import app.menosan.android.core.network.ConnectivityObserver
import app.menosan.android.core.network.safeApiCall
import app.menosan.android.core.settings.AppPreferences
import app.menosan.android.core.settings.ThemeMode
import app.menosan.android.data.local.LocalDataCleaner
import app.menosan.android.data.remote.MenosanApi
import app.menosan.android.feature.auth.SignOutAction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

// Small seams around Android-bound classes, so the Profile and Home view models are plain JVM-testable (AN-4).

/** Whether the device looks online. A hint only: requests can still fail. */
interface NetworkStatus {
    val online: Flow<Boolean>

    fun isOnline(): Boolean
}

/** The Appearance setting (System / Light / Dark), applied by `MainActivity`. */
interface AppearanceStore {
    val themeMode: StateFlow<ThemeMode>

    fun setThemeMode(mode: ThemeMode)
}

enum class ExportResult { SUCCESS, OFFLINE, SERVER_ERROR, WRITE_FAILED }

/** Account actions that need the network or end the session (plan §10 "Account Settings & Privacy"). */
interface AccountActions {
    /** `DELETE /v1/account` (contract §6.7). `204` also when the account is already gone. */
    suspend fun deleteAccount(): ApiResult<Unit>

    /** Streams `GET /v1/export` into the document the user picked with the Storage Access Framework (NFR16). */
    suspend fun exportTo(documentUri: String): ExportResult

    /** Wipes this account's local data (entries, outbox, cached reports, session), then signs out (UFR3). */
    suspend fun endSession()
}

class ConnectivityNetworkStatus @Inject constructor(
    private val connectivity: ConnectivityObserver,
) : NetworkStatus {
    override val online: Flow<Boolean> get() = connectivity.online

    override fun isOnline(): Boolean = connectivity.isOnline()
}

class PreferencesAppearanceStore @Inject constructor(
    private val preferences: AppPreferences,
) : AppearanceStore {
    override val themeMode: StateFlow<ThemeMode> get() = preferences.themeMode

    override fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
}

class DefaultAccountActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MenosanApi,
    private val cleaner: LocalDataCleaner,
    private val signOut: SignOutAction,
) : AccountActions {

    override suspend fun deleteAccount(): ApiResult<Unit> = safeApiCall { api.deleteAccount() }

    override suspend fun exportTo(documentUri: String): ExportResult = withContext(Dispatchers.IO) {
        val body = when (val result = safeApiCall { api.export() }) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> return@withContext if (result.error is ApiError.Network) ExportResult.OFFLINE else ExportResult.SERVER_ERROR
        }
        val uri = Uri.parse(documentUri)
        try {
            body.use { source ->
                val input = source.byteStream()
                val output = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("No output stream")
                output.use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = try {
                            input.read(buffer)
                        } catch (e: IOException) {
                            throw DownloadFailed(e)
                        }
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            ExportResult.SUCCESS
        } catch (e: CancellationException) {
            discard(uri)
            throw e
        } catch (_: DownloadFailed) {
            discard(uri)
            ExportResult.OFFLINE
        } catch (_: Exception) {
            discard(uri)
            ExportResult.WRITE_FAILED
        }
    }

    override suspend fun endSession() {
        cleaner.clearAll()
        signOut()
    }

    /** Removes a half-written export, so the user isn't left with a broken file. */
    private fun discard(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    private class DownloadFailed(cause: IOException) : IOException(cause)

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
    }
}

/** AN-4's Hilt bindings (kept out of `di/` so parallel workstreams don't edit the same file). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    abstract fun networkStatus(impl: ConnectivityNetworkStatus): NetworkStatus

    @Binds
    abstract fun appearanceStore(impl: PreferencesAppearanceStore): AppearanceStore

    @Binds
    abstract fun accountActions(impl: DefaultAccountActions): AccountActions
}
