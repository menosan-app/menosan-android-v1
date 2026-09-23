package app.menosan.android.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation routes (plan §10 screens). Add arguments as data class properties.

@Serializable
data object SignInRoute

@Serializable
data object CreateAccountRoute

@Serializable
data object DashboardRoute

/** Manual logging (AN-1). [entryId] set means "edit this entry". */
@Serializable
data class LogManualRoute(val entryId: String? = null)

/** Photo logging (AN-2). */
@Serializable
data object LogPhotoRoute

/** Current-week entries (AN-1). */
@Serializable
data object EntriesRoute

/** Report history (AN-3). */
@Serializable
data object HistoryRoute

/** One weekly report (AN-3). [weekStart] is `YYYY-MM-DD`. */
@Serializable
data class ReportRoute(val weekStart: String)

/** Account settings and privacy (AN-4). */
@Serializable
data object SettingsRoute
