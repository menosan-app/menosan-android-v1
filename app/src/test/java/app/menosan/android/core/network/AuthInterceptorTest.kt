package app.menosan.android.core.network

import app.menosan.android.core.auth.IdTokenProvider
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    /** Returns "cached" normally and "fresh" when a refresh is forced. Records the calls. */
    private class FakeTokens(private val signedIn: Boolean = true, private val refreshed: String = "fresh") : IdTokenProvider {
        val calls = mutableListOf<Boolean>()
        override fun idToken(forceRefresh: Boolean): String? {
            calls += forceRefresh
            if (!signedIn) return null
            return if (forceRefresh) refreshed else "cached"
        }
    }

    private fun call(tokens: IdTokenProvider): Int {
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens)).build()
        return client.newCall(Request.Builder().url(server.url("/v1/me")).build()).execute().use { it.code }
    }

    @Test
    fun `adds the cached token as a bearer header`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        val tokens = FakeTokens()

        assertEquals(200, call(tokens))
        assertEquals("Bearer cached", server.takeRequest().headers["Authorization"])
        assertEquals(listOf(false), tokens.calls)
    }

    @Test
    fun `retries once with a forced refresh on 401`() {
        server.enqueue(MockResponse.Builder().code(401).body(UNAUTHENTICATED).build())
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        val tokens = FakeTokens()

        assertEquals(200, call(tokens))
        assertEquals("Bearer cached", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer fresh", server.takeRequest().headers["Authorization"])
        assertEquals(listOf(false, true), tokens.calls)
    }

    @Test
    fun `gives up after one retry`() {
        server.enqueue(MockResponse.Builder().code(401).body(UNAUTHENTICATED).build())
        server.enqueue(MockResponse.Builder().code(401).body(UNAUTHENTICATED).build())
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())

        assertEquals(401, call(FakeTokens()))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `does not retry when the refreshed token is the same`() {
        server.enqueue(MockResponse.Builder().code(401).body(UNAUTHENTICATED).build())

        assertEquals(401, call(FakeTokens(refreshed = "cached")))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `sends no header when nobody is signed in`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())

        assertEquals(200, call(FakeTokens(signedIn = false)))
        assertNull(server.takeRequest().headers["Authorization"])
    }

    private companion object {
        const val UNAUTHENTICATED = """{"error":{"code":"UNAUTHENTICATED","message":"Sign in is required.","details":{}}}"""
    }
}
