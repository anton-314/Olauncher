package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShortcutIdentityTest {
    @Test
    fun `identity is stable`() {
        assertEquals(
            shortcutIdentity("org.browser", "https://example.com", "UserHandle{0}"),
            shortcutIdentity("org.browser", "https://example.com", "UserHandle{0}"),
        )
    }

    @Test
    fun `same shortcut id from different browsers has different identity`() {
        assertNotEquals(
            shortcutIdentity("org.browser.one", "https://example.com", "UserHandle{0}"),
            shortcutIdentity("org.browser.two", "https://example.com", "UserHandle{0}"),
        )
    }

    @Test
    fun `field boundaries cannot collide`() {
        assertNotEquals(
            shortcutIdentity("ab", "c", "d"),
            shortcutIdentity("a", "bc", "d"),
        )
    }

    @Test
    fun `fields that contain digits and colons cannot fake a boundary`() {
        // "1:" inside a field looks like a length prefix
        assertNotEquals(
            shortcutIdentity("a", "1:b", ""),
            shortcutIdentity("a1:b", "", ""),
        )
        assertNotEquals(
            shortcutIdentity("p", "id3:x", "u"),
            shortcutIdentity("p", "id", "3:xu"),
        )
        assertNotEquals(
            shortcutIdentity("p", "", "u"),
            shortcutIdentity("p", "u", ""),
        )
    }

    @Test
    fun `same shortcut in another profile has a different identity`() {
        assertNotEquals(
            shortcutIdentity("org.browser", "id", "UserHandle{0}"),
            shortcutIdentity("org.browser", "id", "UserHandle{10}"),
        )
    }

    @Test
    fun `empty and unicode fields are encoded unambiguously`() {
        assertEquals("shortcut:0:0:0:", shortcutIdentity("", "", ""))
        assertEquals("shortcut:1:p2:äö1:u", shortcutIdentity("p", "äö", "u"))
    }

    @Test
    fun `identity never equals a settings key`() {
        // Renames are stored under this identity in the same file as the settings
        val identity = shortcutIdentity("org.browser", "APP_NAME_1", "UserHandle{0}")
        assert(identity.startsWith("shortcut:"))
        assertNotEquals("APP_NAME_1", identity)
    }
}
