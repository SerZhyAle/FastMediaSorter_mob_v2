package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.BrowseSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.StreamDefaultSort
import com.sza.fastmediasorter.domain.repository.RawAuthSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpCookie

/**
 * S0406: pure-JVM round-trip checks for the new backup payload sections.
 * Network-credential mapping is excluded here because it relies on the Android Keystore
 * (CryptoHelper) which is unavailable in a plain JVM unit test.
 */
class BackupMapperRoundTripTest {

    @Test
    fun webAuthSession_roundTrip_preservesCookieFields() {
        val cookie = HttpCookie("sid", "abc123").apply {
            domain = "example.com"
            path = "/app"
            secure = true
            isHttpOnly = true
            maxAge = 3600
        }
        val raw = RawAuthSession(
            host = "example.com",
            accountId = "acc-1",
            displayName = "Alice",
            userAgent = "UA/1.0",
            savedAtEpochMillis = 1_000L,
            lastUsedAtEpochMillis = 2_000L,
            cookies = listOf(cookie),
        )

        val backup = BackupMapper.toBackupWebAuthSession(raw)
        assertEquals(1, backup.cookies.size)
        val backupCookie = backup.cookies.first()
        assertEquals("sid", backupCookie.name)
        assertEquals("abc123", backupCookie.value)
        assertEquals("example.com", backupCookie.domain)
        assertEquals("/app", backupCookie.path)
        assertTrue(backupCookie.secure)
        assertTrue(backupCookie.httpOnly)
        assertNotNull(backupCookie.expiresAtEpochMillis)
    }

    @Test
    fun webAuthSession_roundTrip_preservesSessionMetadata() {
        val raw = RawAuthSession(
            host = "site.org",
            accountId = "uuid-42",
            displayName = "Bob",
            userAgent = "Mozilla/5.0",
            savedAtEpochMillis = 111L,
            lastUsedAtEpochMillis = 222L,
            cookies = listOf(HttpCookie("c", "v").apply { domain = "site.org"; path = "/" }),
        )

        val restored = BackupMapper.toRawAuthSession(BackupMapper.toBackupWebAuthSession(raw))

        assertEquals("site.org", restored.host)
        assertEquals("uuid-42", restored.accountId)
        assertEquals("Bob", restored.displayName)
        assertEquals("Mozilla/5.0", restored.userAgent)
        assertEquals(1, restored.cookies.size)
        assertEquals("c", restored.cookies.first().name)
        assertEquals("v", restored.cookies.first().value)
    }

    @Test
    fun webAuthSession_persistentCookie_keepsLiveExpiryAfterRoundTrip() {
        val raw = RawAuthSession(
            host = "h.com",
            accountId = "a",
            displayName = "",
            userAgent = null,
            savedAtEpochMillis = 0L,
            lastUsedAtEpochMillis = 0L,
            cookies = listOf(HttpCookie("k", "x").apply { domain = "h.com"; path = "/"; maxAge = 7200 }),
        )

        val restored = BackupMapper.toRawAuthSession(BackupMapper.toBackupWebAuthSession(raw))

        // A persistent cookie must survive as persistent (maxAge >= 0), not collapse to a session cookie.
        assertTrue(restored.cookies.first().maxAge >= 0L)
    }

    @Test
    fun payload_v4Json_withoutSecretSections_deserializesWithNullSections() {
        val v4Json = """
            {
              "version": 4,
              "appVersionName": "2.0",
              "settings": { "language": "ru" },
              "resources": [],
              "favorites": []
            }
        """.trimIndent()

        val payload = Gson().fromJson(v4Json, BackupPayload::class.java)

        assertEquals(4, payload.version)
        assertNotNull(payload.settings)
        assertEquals("ru", payload.settings?.language)
        // New v5 sections are absent in a v4 file - must stay null, never crash.
        assertNull(payload.networkCredentials)
        assertNull(payload.webAuthSessions)
    }

    @Test
    fun settings_roundTrip_preservesDefaultNetworkCredentials() {
        val backupSettings = BackupSettings(defaultUser = "domain\\user", defaultPassword = "p@ss")

        val roundTripped = Gson().fromJson(Gson().toJson(backupSettings), BackupSettings::class.java)

        assertEquals("domain\\user", roundTripped.defaultUser)
        assertEquals("p@ss", roundTripped.defaultPassword)
    }

    /**
     * S2632: the two launcher fields this ticket returned to [BackupSettings]. A value the user actually
     * set has to come back after a round trip - failing that is the defect the ticket is named for.
     */
    @Test
    fun launcherSpeedAndPalette_surviveTheRoundTrip() {
        val configured = AppSettings().let {
            it.copy(
                launcher = it.launcher.copy(
                    trayShowSpeed = true,
                    animationPalette = AppSettings.ANIMATION_PALETTE_GREEN
                )
            )
        }

        val restored = BackupMapper.toAppSettings(
            BackupMapper.toBackupSettings(configured),
            AppSettings(),
            BackupPayload.CURRENT_VERSION
        )

        assertTrue(restored.launcherTrayShowSpeed)
        assertEquals(AppSettings.ANIMATION_PALETTE_GREEN, restored.launcherAnimationPalette)
    }

    /**
     * S2632: a backup written BEFORE these fields existed carries neither key, so both arrive null and the
     * restore must keep what the device already has. A non-null default would instead reset the user's real
     * setting on every restore from an existing file - the same silent loss, moved one step later. This is
     * the test that makes the fields nullable rather than defaulted.
     */
    @Test
    fun backupWrittenBeforeTheFieldsExisted_keepsCurrentLauncherSettings() {
        val current = AppSettings().let {
            it.copy(
                launcher = it.launcher.copy(
                    trayShowSpeed = true,
                    animationPalette = AppSettings.ANIMATION_PALETTE_PINK
                )
            )
        }

        val restored = BackupMapper.toAppSettings(BackupSettings(), current, BackupPayload.CURRENT_VERSION)

        assertTrue(restored.launcherTrayShowSpeed)
        assertEquals(AppSettings.ANIMATION_PALETTE_PINK, restored.launcherAnimationPalette)
    }

    /**
     * S2648: the screenshot-gesture group was absent from the backup whole, so a user who had configured
     * twelve gestures across four zones restored to none of them. Comparing the entire nested object
     * rather than a sample is deliberate - the defect was the group missing, not a field within it.
     */
    @Test
    fun screenshotGestureGroup_roundTrip_preservesEveryZoneActionAndPayload() {
        val action = ScreenshotGestureAction.entries.first { it != ScreenshotGestureAction.DO_NOT_USE }
        val configured = AppSettings().let {
            it.copy(
                screenshotGesture = it.screenshotGesture.copy(
                    zoneLeftTopEnabled = false,
                    zoneLeftBottomEnabled = true,
                    zoneRightTopEnabled = true,
                    zoneRightBottomEnabled = true,
                    zoneLeftTopStripVisible = true,
                    zoneRightBottomStripVisible = true,
                    rightBottomUp = action,
                    leftBottomRight = action,
                    payloadRightBottomUp = "https://example.org/one",
                    payloadLeftBottomRight = "com.example.app"
                )
            )
        }

        val restored = BackupMapper.toAppSettings(
            BackupMapper.toBackupSettings(configured),
            AppSettings(),
            BackupPayload.CURRENT_VERSION
        )

        assertEquals(configured.screenshotGesture, restored.screenshotGesture)
    }

    /**
     * S2648: both swipe families are sealed interfaces persisted by `persistedName`, so this proves the
     * backup reuses that token instead of an encoding of its own that only round-trips within one build.
     */
    @Test
    fun launcherSwipeGroup_roundTrip_preservesActionsPayloadsAndScreenCount() {
        val configured = AppSettings().let {
            it.copy(
                launcher = it.launcher.copy(
                    desktopSwipeUpAction = LauncherDesktopSwipeAction.NextScreen,
                    desktopSwipeLeftAction = LauncherDesktopSwipeAction.PreviousScreen,
                    desktopSwipeUpPayload = "com.example.desktop",
                    allAppsSwipeLeftAction = LauncherAllAppsSwipeAction.BackToDesktop,
                    allAppsSwipeLeftPayload = "com.example.allapps",
                    screenCount = 5,
                    weatherLastLocation = "50.45,30.52",
                    widgetBackdropAlpha = 1.0f
                )
            )
        }

        val restored = BackupMapper.toAppSettings(
            BackupMapper.toBackupSettings(configured),
            AppSettings(),
            BackupPayload.CURRENT_VERSION
        )

        assertEquals(configured.launcher, restored.launcher)
    }

    /**
     * S2648: one field per remaining group, each an enum or a set, because those are the values that need
     * an encoding step and so are the ones an encoding mistake would drop.
     */
    @Test
    fun appLevelGroups_roundTrip_preserveEnumsAndSets() {
        val defaults = AppSettings()
        val sort = StreamDefaultSort.entries.first { it != defaults.streamsDefaultSort }
        val swipe = BrowseSwipeAction.entries.first { it != defaults.browseSwipeLeftAction }
        val configured = defaults.copy(
            enableStreams = true,
            streamsDefaultSort = sort,
            colorTheme = "DARK",
            browseSwipeLeftAction = swipe,
            enabledShareTargets = setOf("target-a", "target-b"),
            cameraGeotagEnabled = true,
            enableStopwatch = true,
            playerShowFps = true
        )

        val restored = BackupMapper.toAppSettings(
            BackupMapper.toBackupSettings(configured),
            defaults,
            BackupPayload.CURRENT_VERSION
        )

        assertTrue(restored.enableStreams)
        assertEquals(sort, restored.streamsDefaultSort)
        assertEquals("DARK", restored.colorTheme)
        assertEquals(swipe, restored.browseSwipeLeftAction)
        assertEquals(setOf("target-a", "target-b"), restored.enabledShareTargets)
        assertTrue(restored.cameraGeotagEnabled)
        assertTrue(restored.enableStopwatch)
        assertTrue(restored.playerShowFps)
    }

    /**
     * S2648: a backup written before the groups existed carries none of them, so every group arrives null
     * and the restore must leave all 124 settings as the device already has them. This is the test that
     * makes the groups nullable, and it is the reason `BackupPayload.CURRENT_VERSION` needed no bump.
     */
    @Test
    fun backupWrittenBeforeTheGroupsExisted_keepsEveryGroupedSetting() {
        val current = AppSettings().let {
            it.copy(
                colorTheme = "DARK",
                enableStreams = true,
                cameraGeotagEnabled = true,
                enabledShareTargets = setOf("target-a"),
                screenshotGesture = it.screenshotGesture.copy(zoneRightBottomEnabled = true),
                launcher = it.launcher.copy(
                    screenCount = 4,
                    desktopSwipeUpAction = LauncherDesktopSwipeAction.NextScreen
                )
            )
        }

        val restored = BackupMapper.toAppSettings(BackupSettings(), current, BackupPayload.CURRENT_VERSION)

        assertEquals("DARK", restored.colorTheme)
        assertTrue(restored.enableStreams)
        assertTrue(restored.cameraGeotagEnabled)
        assertEquals(setOf("target-a"), restored.enabledShareTargets)
        assertEquals(current.screenshotGesture, restored.screenshotGesture)
        assertEquals(4, restored.launcherScreenCount)
        assertEquals(LauncherDesktopSwipeAction.NextScreen, restored.launcherDesktopSwipeUpAction)
    }

    /**
     * S2648: every test above calls the DTO directly, so none of them exercises the one step that stands
     * between the mapper and a real backup file - Gson. The groups are nested Kotlin data classes whose
     * every parameter has a default, which is the shape Gson constructs through the synthetic no-argument
     * constructor rather than by unsafe allocation, and that is the property this pins.
     */
    @Test
    fun groups_surviveGsonSerialization() {
        val configured = AppSettings().let {
            it.copy(
                colorTheme = "DARK",
                enabledShareTargets = setOf("target-a"),
                screenshotGesture = it.screenshotGesture.copy(
                    zoneRightBottomEnabled = true,
                    payloadRightBottomUp = "https://example.org/one"
                ),
                launcher = it.launcher.copy(
                    screenCount = 4,
                    desktopSwipeUpAction = LauncherDesktopSwipeAction.NextScreen
                )
            )
        }
        val gson = Gson()

        val parsed = gson.fromJson(
            gson.toJson(BackupMapper.toBackupSettings(configured)),
            BackupSettings::class.java
        )
        val restored = BackupMapper.toAppSettings(parsed, AppSettings(), BackupPayload.CURRENT_VERSION)

        assertEquals("DARK", restored.colorTheme)
        assertEquals(setOf("target-a"), restored.enabledShareTargets)
        assertEquals(configured.screenshotGesture, restored.screenshotGesture)
        assertEquals(4, restored.launcherScreenCount)
        assertEquals(LauncherDesktopSwipeAction.NextScreen, restored.launcherDesktopSwipeUpAction)
    }

    /**
     * S2648: a file written before the groups existed carries none of their keys. Gson must leave all
     * eight null rather than materialising defaulted objects, because a defaulted object would apply 124
     * class defaults over the user's real settings - the very loss this ticket removes.
     */
    @Test
    fun legacyJsonWithoutGroupKeys_leavesEveryGroupNull() {
        val parsed = Gson().fromJson(
            """{"preventSleep":false,"language":"ru","maxRecipients":3}""",
            BackupSettings::class.java
        )

        assertNull(parsed.screenshotGesture)
        assertNull(parsed.launcherExtra)
        assertNull(parsed.capture)
        assertNull(parsed.programs)
        assertNull(parsed.streams)
        assertNull(parsed.appearance)
        assertNull(parsed.playerExtra)
        assertNull(parsed.integration)
    }
}
