package com.sza.fastmediasorter.golden

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceGridCellSize
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.usecase.BackupMapper
import com.sza.fastmediasorter.domain.usecase.BackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3371 phase 06 step 06.2: golden proof that a backup written by an older build still restores.
 *
 * WHAT THE APPLY PATH IS. `ApplyBackupPayloadUseCase` needs a Room database, a WorkManager scheduler
 * and six repositories, so it is not the unit under test here. Everything in it that decides how an
 * OLD payload is read is pure and lives in [BackupMapper] plus the version guard the use case states
 * as `check(payload.version <= BackupPayload.CURRENT_VERSION)`; those are what these fixtures drive.
 * The use case's own tolerance of absent sections is asserted as the null-ness of the sections it
 * reads with `?.let`.
 *
 * WHERE EACH FIXTURE'S VERSION COMES FROM - both are versions the tree names explicitly:
 *
 * - `backup_payload_v4.json` is a v4 backup. `BackupPayload` calls `networkCredentials` and
 *   `webAuthSessions` "secret-bearing sections, nullable so older (v4) backups still deserialize"
 *   (S0406), and `ApplyBackupPayloadUseCase` says it is "tolerant of null sections so older (v4)
 *   backups apply without throwing". v4 also predates `launcherCells` (S1740) and the v6 trust
 *   threshold, so its `linkAutoDownloadOpenInPlayer` must NOT be believed (S1346).
 * - `backup_payload_v6.json` is a v6 backup - the version immediately before the one that added
 *   `rawSettings`: "S3130: v6->v7 - [rawSettings] added. A v6 payload carries no raw section and
 *   restores through the typed section alone, exactly as before." Its
 *   `linkAutoDownloadOpenInPlayer` is at the trusted threshold and must be believed.
 *
 * Both fixtures omit `enablePersistentAudioPlayback`, whose own comment states the rule an absent
 * key must follow: "S2247: mirrors the AppSettings default - a backup written before this field
 * existed must not restore as disabled".
 */
class BackupPayloadGoldenCompatTest {

    private val gson = Gson()

    @Test
    fun `a v4 backup deserializes with its secret-bearing and launcher sections absent`() {
        val payload = readFixture("backup_payload_v4.json")

        assertEquals(4, payload.version)
        assertNull("v4 predates the secret sections - they must arrive null, not empty", payload.networkCredentials)
        assertNull(payload.webAuthSessions)
        assertNull("v4 predates S1740 launcher cells", payload.launcherCells)
        assertNull("v4 predates S3130 raw settings", payload.rawSettings)
        assertNotNull(payload.settings)
        assertEquals(1, payload.resources.orEmpty().size)
        assertEquals(1, payload.favorites.orEmpty().size)
    }

    @Test
    fun `a v4 backup passes the version guard the apply path opens with`() {
        val payload = readFixture("backup_payload_v4.json")

        // The literal predicate of ApplyBackupPayloadUseCase's own check(..) - an older payload is
        // accepted, and only a payload from a newer build is refused.
        assertTrue(payload.version <= BackupPayload.CURRENT_VERSION)
    }

    @Test
    fun `a v4 backup restores its settings and is not believed about openInPlayer`() {
        val payload = readFixture("backup_payload_v4.json")
        val backupSettings = checkNotNull(payload.settings)

        val restored = BackupMapper.toAppSettings(backupSettings, AppSettings(), payload.version)

        assertEquals(ResourceGridCellSize.LARGE, restored.resourceGridCellSize)
        assertTrue(restored.isResourceGridMode)
        assertFalse(restored.preventSleep)
        assertEquals(12, restored.networkParallelism)
        assertEquals(4096, restored.cacheSizeMb)
        assertTrue(restored.allFiles)
        assertEquals(20971520L, restored.textSizeMax)
        assertFalse(
            "S1346: pre-v6 payloads always persisted true, so the stored true is not a choice",
            restored.linkAutoDownloadOpenInPlayer
        )
    }

    @Test
    fun `a v6 backup is believed about openInPlayer and still carries no raw settings`() {
        val payload = readFixture("backup_payload_v6.json")
        val backupSettings = checkNotNull(payload.settings)

        val restored = BackupMapper.toAppSettings(backupSettings, AppSettings(), payload.version)

        assertEquals(6, payload.version)
        assertNull("v6 is the last version before S3130 added the raw section", payload.rawSettings)
        assertTrue("v6 is the S1346 trust threshold", restored.linkAutoDownloadOpenInPlayer)
    }

    @Test
    fun `a field added after a fixture was written restores at its declared default`() {
        listOf("backup_payload_v4.json", "backup_payload_v6.json").forEach { fixture ->
            val payload = readFixture(fixture)
            val restored = BackupMapper.toAppSettings(checkNotNull(payload.settings), AppSettings(), payload.version)

            assertTrue(
                "$fixture carries no enablePersistentAudioPlayback key; S2247 forbids restoring it as off",
                restored.enablePersistentAudioPlayback
            )
        }
    }

    @Test
    fun `an old backup's resource and favourite map through the restore mappers`() {
        val payload = readFixture("backup_payload_v4.json")

        val resource = BackupMapper.toMediaResource(checkNotNull(payload.resources).single())
        assertEquals("Family NAS", resource.name)
        assertEquals(ResourceType.SMB, resource.type)
        assertEquals(DisplayMode.GRID, resource.displayMode)
        assertEquals(SortMode.DATE_DESC, resource.sortMode)
        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO), resource.supportedMediaTypes)
        assertFalse("a restored resource stays unverified until it is reached", resource.isAvailable)

        val favorite = BackupMapper.toFavoritesEntity(
            checkNotNull(payload.favorites).single(),
            resolvedResourceId = 7L,
        )
        assertEquals("smb://192.168.1.50/media/2024/beach.jpg", favorite.uri)
        assertEquals(7L, favorite.resourceId)
        assertEquals("beach.jpg", favorite.displayName)
    }

    @Test
    fun `a v6 backup's credential and launcher sections map through the restore mappers`() {
        val payload = readFixture("backup_payload_v6.json")

        val credential = BackupMapper.toNetworkCredentialsEntity(checkNotNull(payload.networkCredentials).single())
        assertEquals("smb-192-168-1-50", credential.credentialId)
        assertEquals("192.168.1.50", credential.server)
        assertEquals(445, credential.port)

        val cell = BackupMapper.toLauncherCellEntity(checkNotNull(payload.launcherCells).single())
        assertEquals("fn:calculator", cell.target)
        // The stored target is a LauncherCellCommand payload, so the restored cell must still decode.
        assertEquals(LauncherCellCommand.Feature("calculator"), LauncherCellCommand.decode(cell.target))

        val session = BackupMapper.toRawAuthSession(checkNotNull(payload.webAuthSessions).single())
        assertEquals("example.org", session.host)
    }

    @Test
    fun `what the current builder writes re-reads into the same restored settings`() {
        val payload = readFixture("backup_payload_v6.json")
        val restored = BackupMapper.toAppSettings(checkNotNull(payload.settings), AppSettings(), payload.version)

        val rebuilt = BackupMapper.toBackupPayload(
            settings = restored,
            resources = emptyList(),
            favorites = emptyList(),
            appVersionCode = 1L,
            appVersionName = "golden"
        )
        val reread = gson.fromJson(gson.toJson(rebuilt), BackupPayload::class.java)

        assertEquals(BackupPayload.CURRENT_VERSION, reread.version)
        val rereadSettings = BackupMapper.toAppSettings(
            checkNotNull(reread.settings),
            AppSettings(),
            reread.version,
        )
        assertEquals(restored, rereadSettings)
    }

    private fun readFixture(fileName: String): BackupPayload {
        val path = "golden/backup/$fileName"
        val json = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Golden fixture $path is missing from the test resources"
        }.bufferedReader().use { it.readText() }
        return gson.fromJson(json, BackupPayload::class.java)
    }
}
