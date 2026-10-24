package `in`.roomcontroller.app

import org.junit.Assert.*
import org.junit.Test

class SpotifyPkceTest {
    @Test fun challengeMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifyPkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun callbackAcceptsOnlyMatchingUnexpiredRequest() {
        assertEquals("a+b", SpotifyPkce.callback("${SpotifyPkce.REDIRECT}?state=expected&code=a%2Bb", "expected", 200, 100))
    }
    @Test fun rejectsWrongOriginStateExpiryDenialAndDuplicateParameters() {
        listOf(
            "other://spotify/callback?state=expected&code=x",
            "in.roomcontroller.app://spotify/other?state=expected&code=x",
            "${SpotifyPkce.REDIRECT}?state=wrong&code=x",
            "${SpotifyPkce.REDIRECT}?state=expected&error=access_denied",
            "${SpotifyPkce.REDIRECT}?state=expected&code=x&code=y",
            "${SpotifyPkce.REDIRECT}?state=expected&code=x#fragment"
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { SpotifyPkce.callback(url, "expected", 200, 100) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyPkce.callback("${SpotifyPkce.REDIRECT}?state=expected&code=x", "expected", 100, 100)
        }
    }
    @Test fun generatedAuthorizationUsesPkceAndAppCallbackWithoutSecret() {
        val verifier = SpotifyPkce.random()
        assertTrue(verifier.length in 43..128)
        assertNotEquals(verifier, SpotifyPkce.random())
        val url = SpotifyPkce.authorization(verifier, "state", "test_client_id_pkce")
        assertTrue(url.contains("client_id=test_client_id_pkce"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("redirect_uri=in.roomcontroller.app%3A%2F%2Fspotify%2Fcallback"))
        assertFalse(url.contains("client_secret"))
        assertFalse(url.contains(verifier))
    }
    @Test fun authorizationRejectsBlankClientId() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyPkce.authorization("verifier", "state", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpotifyPkce.authorization("verifier", "state", "   ")
        }
    }
}
