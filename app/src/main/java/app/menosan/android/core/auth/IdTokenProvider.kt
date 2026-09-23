package app.menosan.android.core.auth

/** Supplies the signed-in user's Firebase ID token to `AuthInterceptor`. Called on OkHttp threads (blocking is fine). */
fun interface IdTokenProvider {
    /** Null when nobody is signed in or the token can't be fetched (e.g. offline with an expired token). */
    fun idToken(forceRefresh: Boolean): String?
}
