package com.example.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Trust guard: `interpretVirtualCard()` used to fall back to `getMockReading()`
 * whenever the AI call timed out or failed, so canned text was rendered to the
 * user as if it were a real Gemini reading. These tests pin the honest
 * behaviour -- failures propagate, and the only mock content that can ever
 * reach the UI is explicitly flagged `isOffline = true`.
 */
@RunWith(RobolectricTestRunner::class)
class GeminiTarotServiceReadingTest {

    private lateinit var server: MockWebServer

    private val validBody = """
        {"candidates":[{"content":{"parts":[{"text":"{\"cardName\":\"The Star\",\"orientation\":\"Upright\",\"summary\":\"Hope returns.\",\"generalMeaning\":\"A real AI meaning.\",\"advice\":\"Real advice.\",\"warning\":\"Real warning.\",\"luckyElements\":[\"Blue\",\"3:33 AM\",\"Number 7\",\"Aquarius / Air\"]}"}]}}]}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun proxyUrl() = server.url("/gemini").toString()

    private fun draw(proxy: String = proxyUrl(), offline: Boolean = false) = runBlocking {
        GeminiTarotService.interpretVirtualCard(
            cardName = "The Star",
            orientation = "Upright",
            proxyUrl = proxy,
            offlineMode = offline,
            idToken = "test-token"
        )
    }

    @Test
    fun `happy path returns the AI reading and is not flagged offline`() {
        server.enqueue(MockResponse().setBody(validBody))

        val reading = draw()

        assertNotNull(reading)
        assertEquals("A real AI meaning.", reading!!.generalMeaning)
        assertFalse("A live AI reading must never be flagged offline", reading.isOffline)
    }

    @Test
    fun `network failure throws instead of silently returning mock text`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        try {
            val reading = draw()
            fail("Expected an exception, got a reading instead: $reading")
        } catch (e: Exception) {
            // Expected. The critical assertion is that nothing readable came back.
            assertTrue(e !is AssertionError)
        }
    }

    @Test
    fun `server error throws instead of silently returning mock text`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))

        try {
            val reading = draw()
            fail("Expected an exception, got a reading instead: $reading")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("boom") == true)
        }
    }

    @Test
    fun `unparseable AI response yields null, never a mock reading`() {
        server.enqueue(MockResponse().setBody("""{"candidates":[]}"""))

        // null makes the ViewModel show TarotUIState.Error; a mock would have
        // rendered as a Success indistinguishable from real AI output.
        assertNull(draw())
    }

    @Test
    fun `missing proxy and api key is a configuration error, not a fake reading`() {
        try {
            val reading = draw(proxy = "")
            fail("Expected NotConfiguredException, got a reading instead: $reading")
        } catch (e: GeminiTarotService.NotConfiguredException) {
            assertTrue(e.message!!.contains("Offline Mode"))
        }
    }

    @Test
    fun `explicit offline mode returns a reading that is flagged as offline`() {
        val reading = draw(offline = true)

        assertNotNull(reading)
        assertTrue(
            "Offline sample readings must be labelled so the UI cannot pass them off as AI",
            reading!!.isOffline
        )
    }

    @Test
    fun `a slow AI response beyond the old 8 second budget still succeeds`() {
        // The bug: interpretVirtualCard ran on the 8s-read textClient inside a
        // 10s coroutine budget. A completion that takes 12s -- entirely normal
        // for an LLM -- used to time out and silently serve canned text.
        server.enqueue(
            MockResponse().setBody(validBody).setBodyDelay(12, TimeUnit.SECONDS)
        )

        val reading = draw()

        assertNotNull("A 12s LLM response must be awaited, not timed out", reading)
        assertEquals("A real AI meaning.", reading!!.generalMeaning)
        assertFalse(reading.isOffline)
    }

    @Test
    fun `getMockReading defaults to unflagged and must be opted into as offline`() {
        // Defensive: if a future caller reintroduces a silent fallback, this
        // documents that the default carries no offline label -- which is why
        // the only legitimate caller is the explicit Offline Mode branch.
        assertFalse(GeminiTarotService.getMockReading("The Star", "Upright").isOffline)
        assertTrue(
            GeminiTarotService.getMockReading("The Star", "Upright", isOffline = true).isOffline
        )
    }
}
