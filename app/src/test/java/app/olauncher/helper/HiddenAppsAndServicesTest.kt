package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppsAndServicesTest {
    private val user = "UserHandle{0}"

    @Test
    fun `legacy hidden apps get the user after a separator`() {
        assertEquals(
            setOf("com.example|$user", "org.other|$user"),
            migrateHiddenApps(setOf("com.example", "org.other"), user)
        )
    }

    @Test
    fun `migrated entries match the key the app list looks up`() {
        val packageName = "com.example"
        val migrated = migrateHiddenApps(setOf(packageName), user)
        assertTrue(migrated.contains("$packageName|$user"))
    }

    @Test
    fun `entries that already have a user are kept as they are`() {
        val current = setOf("com.example|UserHandle{10}", "com.example|$user")
        assertEquals(current, migrateHiddenApps(current, user))
    }

    @Test
    fun `migration of an empty set is empty`() {
        assertTrue(migrateHiddenApps(emptySet(), user).isEmpty())
    }

    private val pkg = "app.olauncher"
    private val cls = "app.olauncher.helper.MyAccessibilityService"

    @Test
    fun `service is found in the full and the short form`() {
        assertTrue(isServiceInEnabledList("$pkg/$cls", pkg, cls))
        assertTrue(isServiceInEnabledList("$pkg/.helper.MyAccessibilityService", pkg, cls))
        assertTrue(isServiceInEnabledList("com.a/com.a.S:$pkg/$cls:com.b/.T", pkg, cls))
        assertTrue(isServiceInEnabledList("com.a/com.a.S : $pkg/$cls", pkg, cls))
    }

    @Test
    fun `service is not found when absent or only similar`() {
        assertFalse(isServiceInEnabledList(null, pkg, cls))
        assertFalse(isServiceInEnabledList("", pkg, cls))
        assertFalse(isServiceInEnabledList("com.a/com.a.S", pkg, cls))
        // Another package whose name ends with ours
        assertFalse(isServiceInEnabledList("evil.$pkg/$cls", pkg, cls))
        assertFalse(isServiceInEnabledList("x$pkg/$cls", pkg, cls))
        // The debug build is a different package
        assertFalse(isServiceInEnabledList("$pkg.debug/$cls", pkg, cls))
        // A different class in our package
        assertFalse(isServiceInEnabledList("$pkg/${cls}2", pkg, cls))
        assertFalse(isServiceInEnabledList("$pkg/", pkg, cls))
        assertFalse(isServiceInEnabledList(cls, pkg, cls))
    }
}
