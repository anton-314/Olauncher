package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrefsTest {
    private lateinit var store: FakeSharedPreferences
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        store = FakeSharedPreferences()
        prefs = Prefs(store)
    }

    private fun fillAllSlots() {
        prefs.appName1 = "name1"; prefs.appPackage1 = "pkg1"; prefs.appUser1 = "user1"; prefs.appActivityClassName1 = "cls1"; prefs.isShortcut1 = false; prefs.shortcutId1 = "id1"
        prefs.appName2 = "name2"; prefs.appPackage2 = "pkg2"; prefs.appUser2 = "user2"; prefs.appActivityClassName2 = "cls2"; prefs.isShortcut2 = true; prefs.shortcutId2 = "id2"
        prefs.appName3 = "name3"; prefs.appPackage3 = "pkg3"; prefs.appUser3 = "user3"; prefs.appActivityClassName3 = "cls3"; prefs.isShortcut3 = false; prefs.shortcutId3 = "id3"
        prefs.appName4 = "name4"; prefs.appPackage4 = "pkg4"; prefs.appUser4 = "user4"; prefs.appActivityClassName4 = "cls4"; prefs.isShortcut4 = true; prefs.shortcutId4 = "id4"
        prefs.appName5 = "name5"; prefs.appPackage5 = "pkg5"; prefs.appUser5 = "user5"; prefs.appActivityClassName5 = "cls5"; prefs.isShortcut5 = false; prefs.shortcutId5 = "id5"
        prefs.appName6 = "name6"; prefs.appPackage6 = "pkg6"; prefs.appUser6 = "user6"; prefs.appActivityClassName6 = "cls6"; prefs.isShortcut6 = true; prefs.shortcutId6 = "id6"
        prefs.appName7 = "name7"; prefs.appPackage7 = "pkg7"; prefs.appUser7 = "user7"; prefs.appActivityClassName7 = "cls7"; prefs.isShortcut7 = false; prefs.shortcutId7 = "id7"
        prefs.appName8 = "name8"; prefs.appPackage8 = "pkg8"; prefs.appUser8 = "user8"; prefs.appActivityClassName8 = "cls8"; prefs.isShortcut8 = true; prefs.shortcutId8 = "id8"
    }

    @Test
    fun `location getters read the matching slot for all 8 slots`() {
        fillAllSlots()
        for (i in 1..8) {
            assertEquals("name$i", prefs.getAppName(i))
            assertEquals("pkg$i", prefs.getAppPackage(i))
            assertEquals("user$i", prefs.getAppUser(i))
            assertEquals("cls$i", prefs.getAppActivityClassName(i))
            assertEquals("id$i", prefs.getShortcutId(i))
            assertEquals(i % 2 == 0, prefs.getIsShortcut(i))
        }
    }

    @Test
    fun `location getters return empty values outside 1 to 8`() {
        fillAllSlots()
        for (i in listOf(0, 9, -1, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals("", prefs.getAppName(i))
            assertEquals("", prefs.getAppPackage(i))
            assertEquals("", prefs.getAppUser(i))
            assertEquals("", prefs.getAppActivityClassName(i))
            assertEquals("", prefs.getShortcutId(i))
            assertFalse(prefs.getIsShortcut(i))
        }
    }

    @Test
    fun `empty prefs give empty slots`() {
        for (i in 1..8) {
            assertEquals("", prefs.getAppName(i))
            assertEquals("", prefs.getAppPackage(i))
            assertFalse(prefs.getIsShortcut(i))
        }
    }

    @Test
    fun `setAppActivityClassName writes only the given slot`() {
        fillAllSlots()
        prefs.setAppActivityClassName(5, "new")
        for (i in 1..8) assertEquals(if (i == 5) "new" else "cls$i", prefs.getAppActivityClassName(i))
        prefs.setAppActivityClassName(9, "ignored")
        prefs.setAppActivityClassName(0, "ignored")
        for (i in 1..8) assertEquals(if (i == 5) "new" else "cls$i", prefs.getAppActivityClassName(i))
    }

    @Test
    fun `null activity class name reads back as empty, not as the text null`() {
        prefs.appActivityClassName1 = "cls"
        prefs.appActivityClassName1 = null
        assertEquals("", prefs.appActivityClassName1)
        assertEquals("", prefs.getAppActivityClassName(1))
    }

    @Test
    fun `updateAppActivityClassName updates every place that uses the package`() {
        fillAllSlots()
        prefs.appPackage7 = "pkg3"
        prefs.clockAppPackage = "pkg3"
        prefs.calendarAppPackage = "other"
        prefs.calendarAppClassName = "calendarCls"
        prefs.screenTimeAppPackage = "pkg3"
        prefs.appPackageSwipeLeft = "pkg3"
        prefs.appPackageSwipeRight = "other"
        prefs.appActivityClassNameRight = "rightCls"

        prefs.updateAppActivityClassName("pkg3", "moved")

        assertEquals("moved", prefs.getAppActivityClassName(3))
        assertEquals("moved", prefs.getAppActivityClassName(7))
        assertEquals("cls1", prefs.getAppActivityClassName(1))
        assertEquals("moved", prefs.clockAppClassName)
        assertEquals("moved", prefs.screenTimeAppClassName)
        assertEquals("moved", prefs.appActivityClassNameSwipeLeft)
        assertEquals("calendarCls", prefs.calendarAppClassName)
        assertEquals("rightCls", prefs.appActivityClassNameRight)
    }

    @Test
    fun `hidden apps default to an empty mutable set`() {
        val hidden = prefs.hiddenApps
        assertTrue(hidden.isEmpty())
        hidden.add("works")
    }

    @Test
    fun `paused apps toggle and persist independently of the hidden apps`() {
        assertTrue(prefs.pausedApps.isEmpty())
        val paused = prefs.pausedApps
        paused.add("com.instagram.android")
        assertTrue(prefs.pausedApps.isEmpty())
        prefs.pausedApps = paused
        assertEquals(setOf("com.instagram.android"), prefs.pausedApps)
        assertTrue(prefs.hiddenApps.isEmpty())
        prefs.pausedApps = prefs.pausedApps.apply { remove("com.instagram.android") }
        assertTrue(prefs.pausedApps.isEmpty())
    }

    @Test
    fun `breathing pause grant holds only for that package and a few seconds`() {
        assertFalse(prefs.isBreathingPauseGranted("com.instagram.android"))
        prefs.grantBreathingPause("com.instagram.android", now = 10_000L)
        assertTrue(prefs.isBreathingPauseGranted("com.instagram.android", now = 10_000L))
        assertTrue(prefs.isBreathingPauseGranted("com.instagram.android", now = 15_000L))
        assertFalse(prefs.isBreathingPauseGranted("com.instagram.android", now = 15_001L))
        assertFalse(prefs.isBreathingPauseGranted("com.other.app", now = 10_000L))
        // A grant from the future, e.g. after the clock was moved back, does not count
        assertFalse(prefs.isBreathingPauseGranted("com.instagram.android", now = 9_000L))
    }

    @Test
    fun `anki review is off by default and the card count stays within 1 to the maximum`() {
        assertEquals(0L, prefs.ankiDeckId)
        assertEquals(2, prefs.ankiCardCount)
        prefs.ankiCardCount = 7
        assertEquals(7, prefs.ankiCardCount)
        prefs.ankiCardCount = Prefs.ANKI_MAX_CARD_COUNT + 1
        assertEquals(Prefs.ANKI_MAX_CARD_COUNT, prefs.ankiCardCount)
        prefs.ankiCardCount = 0
        assertEquals(1, prefs.ankiCardCount)
    }

    @Test
    fun `hidden apps getter returns a copy that does not change the stored set`() {
        prefs.hiddenApps = mutableSetOf("a|UserHandle{0}")
        val hidden = prefs.hiddenApps
        hidden.add("b|UserHandle{0}")
        assertEquals(setOf("a|UserHandle{0}"), prefs.hiddenApps)
    }

    @Test
    fun `adding to the hidden apps and saving persists the change`() {
        prefs.hiddenApps = mutableSetOf("a|UserHandle{0}")
        val hidden = prefs.hiddenApps
        hidden.add("b|UserHandle{0}")
        prefs.hiddenApps = hidden
        assertEquals(setOf("a|UserHandle{0}", "b|UserHandle{0}"), prefs.hiddenApps)
        // The stored set must not be the instance callers keep changing
        hidden.add("c|UserHandle{0}")
        assertEquals(2, prefs.hiddenApps.size)
    }

    @Test
    fun `rename label round trip`() {
        assertEquals("", prefs.getAppRenameLabel("org.example"))
        prefs.setAppRenameLabel("org.example", "Mail")
        assertEquals("Mail", prefs.getAppRenameLabel("org.example"))
    }

    @Test
    fun `blank rename label clears the rename`() {
        prefs.setAppRenameLabel("org.example", "Mail")
        prefs.setAppRenameLabel("org.example", "   ")
        assertEquals("", prefs.getAppRenameLabel("org.example"))
        assertFalse(store.contains("org.example"))
        prefs.setAppRenameLabel("org.example", "Mail")
        prefs.setAppRenameLabel("org.example", "")
        assertFalse(store.contains("org.example"))
    }

    @Test
    fun `rename lookup with a key that holds a non-string setting returns empty`() {
        // Shortcut ids are chosen by other apps and are also looked up as rename keys
        prefs.hiddenApps = mutableSetOf("x|y")
        prefs.firstOpen = false
        prefs.homeAppsNum = 6
        prefs.firstOpenTime = 123L
        prefs.textSizeScale = 1.2f
        assertEquals("", prefs.getAppRenameLabel("HIDDEN_APPS"))
        assertEquals("", prefs.getAppRenameLabel("FIRST_OPEN"))
        assertEquals("", prefs.getAppRenameLabel("HOME_APPS_NUM"))
        assertEquals("", prefs.getAppRenameLabel("FIRST_OPEN_TIME"))
        assertEquals("", prefs.getAppRenameLabel("TEXT_SIZE_SCALE"))
    }

    @Test
    fun `rename label for a shortcut identity does not touch settings`() {
        prefs.appName1 = "Home app"
        val identity = shortcutIdentity("org.browser", "APP_NAME_1", "UserHandle{0}")
        prefs.setAppRenameLabel(identity, "Renamed")
        assertEquals("Home app", prefs.appName1)
        assertEquals("Renamed", prefs.getAppRenameLabel(identity))
    }

    @Test
    fun `text size scale is kept in the supported range`() {
        assertEquals(1.0f, prefs.textSizeScale, 0f)
        prefs.textSizeScale = 1.3f
        assertEquals(1.3f, prefs.textSizeScale, 0f)
        prefs.textSizeScale = 0f
        assertEquals(Prefs.MIN_TEXT_SIZE_SCALE, prefs.textSizeScale, 0f)
        prefs.textSizeScale = -4f
        assertEquals(Prefs.MIN_TEXT_SIZE_SCALE, prefs.textSizeScale, 0f)
        prefs.textSizeScale = 25f
        assertEquals(Prefs.MAX_TEXT_SIZE_SCALE, prefs.textSizeScale, 0f)
        prefs.textSizeScale = Float.POSITIVE_INFINITY
        assertEquals(Prefs.MAX_TEXT_SIZE_SCALE, prefs.textSizeScale, 0f)
        prefs.textSizeScale = Float.NaN
        assertEquals(1.0f, prefs.textSizeScale, 0f)
    }

    @Test
    fun `defaults for a fresh install`() {
        assertTrue(prefs.firstOpen)
        assertEquals(4, prefs.homeAppsNum)
        assertEquals(Constants.DateTime.ON, prefs.dateTimeVisibility)
        assertEquals("Camera", prefs.appNameSwipeLeft)
        assertEquals("Phone", prefs.appNameSwipeRight)
        assertTrue(prefs.swipeLeftEnabled)
        assertTrue(prefs.swipeRightEnabled)
        assertFalse(prefs.lockModeOn)
        assertEquals("", prefs.dailyWallpaperUrl)
    }
}
