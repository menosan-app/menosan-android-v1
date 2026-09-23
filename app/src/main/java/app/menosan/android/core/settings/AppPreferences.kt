package app.menosan.android.core.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Appearance setting (Profile → Light / Dark mode). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Device-level app preferences. They survive logout, since they're not account data. */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    private companion object {
        const val PREFS = "menosan_prefs"
        const val KEY_THEME = "theme_mode"
    }
}
