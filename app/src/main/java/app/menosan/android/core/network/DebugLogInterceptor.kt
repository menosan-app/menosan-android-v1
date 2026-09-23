package app.menosan.android.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Debug builds only: logs method, path, status, duration, and the request id. Never bodies, headers, tokens,
 * or query strings (plan §14).
 */
class DebugLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        val response = chain.proceed(request)
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.d(TAG, "${request.method} ${request.url.encodedPath} → ${response.code} (${ms} ms, ${response.header(REQUEST_ID_HEADER)})")
        return response
    }

    private companion object {
        const val TAG = "MenosanHttp"
    }
}
