package app.olauncher.helper

import app.olauncher.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class UrlSafetyTest {

    @Test
    fun `https links with a host are accepted`() {
        assertTrue(isSecureWebUrl("https://images.unsplash.com/photo-1512551980832-13df02babc9e"))
        assertTrue(isSecureWebUrl("https://duck.co/?q=%21w+test"))
        assertTrue(isSecureWebUrl("HTTPS://EXAMPLE.COM/path"))
        assertTrue(isSecureWebUrl("https://example.com:8443/a?b=c#d"))
    }

    @Test
    fun `all built-in links pass`() {
        listOf(
            Constants.URL_ABOUT_OLAUNCHER, Constants.URL_OLAUNCHER_PRIVACY, Constants.URL_DOUBLE_TAP,
            Constants.URL_OLAUNCHER_GITHUB, Constants.URL_OLAUNCHER_PLAY_STORE, Constants.URL_OLAUNCHER_PRO,
            Constants.URL_PLAY_STORE_DEV, Constants.URL_TWITTER_TANUJ, Constants.URL_WALLPAPERS,
            Constants.URL_NTS, Constants.URL_PENTASTIC, Constants.URL_DEFAULT_DARK_WALLPAPER,
            Constants.URL_DEFAULT_LIGHT_WALLPAPER, Constants.URL_DIGITAL_WELLBEING_LEARN_MORE,
        ).forEach { assertTrue(it, isSecureWebUrl(it)) }
    }

    @Test
    fun `other schemes are rejected`() {
        listOf(
            "http://example.com/wall.jpg",
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end",
            "javascript:alert(1)",
            "file:///sdcard/Download/x.jpg",
            "content://com.android.contacts/contacts",
            "market://details?id=app.olauncher",
            "ftp://example.com/file",
            "data:text/html,<script>alert(1)</script>",
            "jar:https://example.com/a.jar!/x",
        ).forEach { assertFalse(it, isSecureWebUrl(it)) }
    }

    @Test
    fun `malformed or incomplete values are rejected`() {
        listOf(
            null, "", "   ", "https://", "https:///path", "https:example.com",
            "//example.com/x", "example.com", " https://example.com", "https://exa mple.com",
            "https://user:pass@example.com/", "https://evil.com@example.com/",
        ).forEach { assertFalse(it.toString(), isSecureWebUrl(it)) }
    }

    @Test
    fun `bang search query is encoded into a single query parameter`() {
        val url = buildBangSearchUrl("!w a&b=c#d")
        assertTrue(url.startsWith(Constants.URL_DUCK_SEARCH))
        val uri = URI(url)
        assertEquals("duck.co", uri.host)
        assertEquals(null, uri.rawFragment)
        assertEquals("q=%21w+a%26b%3Dc%23d", uri.rawQuery)
        assertTrue(isSecureWebUrl(url))
    }

    @Test
    fun `bang search keeps non-ascii text and percent signs intact`() {
        assertEquals(Constants.URL_DUCK_SEARCH + "%21g+M%C3%BCnchen+100%25", buildBangSearchUrl("!g München 100%"))
    }

    @Test
    fun `bang search cannot break out of the duck host`() {
        val uri = URI(buildBangSearchUrl("!x @evil.com/../ \\ https://evil.com"))
        assertEquals("duck.co", uri.host)
        assertEquals(null, uri.rawUserInfo)
    }
}
