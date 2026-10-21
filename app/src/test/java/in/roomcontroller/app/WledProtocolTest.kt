package `in`.roomcontroller.app

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WledProtocolTest {
    private fun state(power: Boolean = true) = obj("state" to obj("on" to power, "bri" to 128),
        "info" to obj("ver" to "test", "mac" to "AA:BB:CC:11:22:33", "leds" to obj("count" to 300)))

    @Test fun acceptsIpv4HostnamesPortsAndIpv6() {
        assertEquals("http://192.168.1.105", wledOrigin("192.168.1.105/"))
        assertEquals("http://wled.local:8080", wledOrigin("HTTP://WLED.local:8080"))
        assertEquals("http://[fd00::1]", wledOrigin("http://[fd00::1]"))
    }
    @Test fun rejectsCredentialsPathsQueriesAndNonWebSchemes() {
        listOf("http://owner:secret@wled.local", "http://wled.local/settings", "http://wled.local?token=abc",
            "http://wled.local#anything", "file:///etc/config", "javascript:alert(1)", "http://wled.local:99999").forEach {
            assertTrue(it, runCatching { wledOrigin(it) }.isFailure)
        }
    }
    @Test fun originBoundaryIncludesSchemeAndPort() {
        assertTrue(sameWledOrigin("http://wled.local", "http://wled.local:80/settings"))
        assertFalse(sameWledOrigin("http://wled.local", "http://wled.local.evil/settings"))
        assertFalse(sameWledOrigin("http://wled.local", "https://wled.local/settings"))
        assertFalse(sameWledOrigin("http://wled.local", "http://wled.local:8080/settings"))
        assertFalse(sameWledOrigin("http://wled.local", "http://user@wled.local/settings"))
    }
    @Test fun roomFirmwareResponseIsNotAcceptedAsWled() {
        assertTrue(runCatching { WledSnapshot.parse(obj("runtime" to obj("version" to "1.1.3-controls"))) }.isFailure)
        assertEquals("aabbcc112233", wledDeviceId(state().getJSONObject("info")))
    }
    @Test fun sendsRealCommandThenReadsAuthoritativeState() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"success\":true}"))
            server.enqueue(MockResponse().setBody(state(false).toString())); server.start()
            val result = WledApi().command(server.url("/").toString(), obj("on" to false))
            val command = server.takeRequest(); val payload = JSONObject(command.body.readUtf8())
            assertEquals("POST", command.method); assertEquals("/json/state", command.path)
            assertNull(command.getHeader("Authorization")); assertFalse(payload.getBoolean("on")); assertTrue(payload.getBoolean("v"))
            assertFalse(result.state.getBoolean("on")); assertEquals("/json/si", server.takeRequest().path)
        }
    }
    @Test fun failedAcknowledgementDoesNotBecomeSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"error\":9}")); server.start()
            assertTrue(runCatching { WledApi().command(server.url("/").toString(), obj("ps" to 1)) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun redirectCannotForwardACommandToAnotherHost() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/redirected")); server.start()
            assertTrue(runCatching { WledApi().command(server.url("/").toString(), obj("on" to true)) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun disconnectDoesNotReplayAPost() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)); server.start()
            val client = OkHttpClient.Builder().readTimeout(300, java.util.concurrent.TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
            assertTrue(runCatching { WledApi(client).command(server.url("/").toString(), obj("ps" to 4)) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun brightnessIsClampedToTheWledByteRange() {
        assertEquals(0, wledByte(-1f)); assertEquals(255, wledByte(101f)); assertEquals(255, wledByte(100f))
    }
}
