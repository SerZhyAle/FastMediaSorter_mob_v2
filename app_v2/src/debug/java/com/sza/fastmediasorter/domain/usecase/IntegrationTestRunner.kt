package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.AudioMetadata
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

import javax.inject.Inject
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.local.db.CryptoHelper

/**
 * Automated integration test runner for file operations.
 * Tests all combinations of Copy/Move/Rename/Delete across all resource types.
 * Also tests image editing, GIF processing, and other app features.
 */
class IntegrationTestRunner @Inject constructor(
    private val context: Context,
    private val fileOperationUseCase: FileOperationUseCase,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val credentialsLoader: TestCredentialsLoader,
    private val resourceRepository: com.sza.fastmediasorter.domain.repository.ResourceRepository,
    // Image editing use cases
    private val rotateImageUseCase: RotateImageUseCase,
    private val flipImageUseCase: FlipImageUseCase,
    private val applyImageFilterUseCase: ApplyImageFilterUseCase,
    private val adjustImageUseCase: AdjustImageUseCase,
    // GIF use cases
    private val extractGifFramesUseCase: ExtractGifFramesUseCase,
    private val saveGifFirstFrameUseCase: SaveGifFirstFrameUseCase,
    private val changeGifSpeedUseCase: ChangeGifSpeedUseCase,
    // Settings use cases
    private val exportSettingsUseCase: ExportSettingsUseCase,
    private val importSettingsUseCase: ImportSettingsUseCase,
    // Favorites use case
    private val favoritesUseCase: FavoritesUseCase,
    // Metadata use cases  
    private val extractExifMetadataUseCase: ExtractExifMetadataUseCase,
    private val extractVideoMetadataUseCase: ExtractVideoMetadataUseCase,
    // Audio use cases
    private val searchLyricsUseCase: SearchLyricsUseCase,
    private val searchAudioCoverUseCase: SearchAudioCoverUseCase,
    // Network utility use cases
    private val networkSpeedTestUseCase: NetworkSpeedTestUseCase,
    private val discoverNetworkResourcesUseCase: DiscoverNetworkResourcesUseCase,
    // Download use case
    private val downloadNetworkFileUseCase: DownloadNetworkFileUseCase,
    // Network image edit use case
    private val networkImageEditUseCase: NetworkImageEditUseCase,
    // Cache use case
    private val calculateOptimalCacheSizeUseCase: CalculateOptimalCacheSizeUseCase,
    // Scan use case
    private val scanLocalFoldersUseCase: ScanLocalFoldersUseCase
) {
    
    /**
     * Test group categories for selective test execution.
     */
    enum class TestGroup(val displayName: String) {
        LOCAL("Local Operations"),
        NETWORK_COPY("Network Copy"),
        MATRIX_COPY("Matrix Copy (N×N)"),
        MATRIX_MOVE("Matrix Move (N×N)"),
        MATRIX_OTHER("Matrix Rename/Delete"),
        IMAGE("Image Editing"),
        GIF("GIF Processing"),
        SETTINGS("Settings"),
        FAVORITES("Favorites"),
        METADATA("Metadata"),
        CLOUD_PROVIDERS("Cloud Providers"),
        AUDIO("Audio Features"),
        NETWORK_UTILS("Network Utilities"),
        CACHE("Cache Management"),
        SCANNING("Media Scanning"),
        FAILED_ONLY("Failed Tests Only"),
        ALL("All Tests")
    }
    
    data class TestResult(
        val testName: String,
        val operation: String,
        val sourceType: String,
        val destType: String?,
        val success: Boolean,
        val duration: Long,
        val error: String? = null,
        val details: String? = null
    )
    
    data class TestProgress(
        val currentTest: Int,
        val totalTests: Int,
        val testName: String,
        val status: String
    )
    
    private val testResults = mutableListOf<TestResult>()
    private val logBuilder = StringBuilder()

    /**
     * Inject test credentials into the repository so that
     * SmbFileOperationHandler can find them during tests.
     */
    private suspend fun initializeTestCredentials() {
        try {
            log("Initializing test credentials...")
            val credentials = credentialsLoader.loadCredentials()
            
            for (cred in credentials) {
                // Determine type string
                val typeStr = when (cred.type) {
                    ResourceType.SMB -> "SMB"
                    ResourceType.SFTP -> "SFTP"
                    ResourceType.FTP -> "FTP"
                    else -> continue // Skip others for now
                }
                
                // create() handles encryption
                val entity = NetworkCredentialsEntity.create(
                    credentialId = UUID.randomUUID().toString(),
                    type = typeStr,
                    server = cred.server,
                    port = cred.port ?: (if (typeStr == "SMB") 445 else 22),
                    username = cred.username,
                    plaintextPassword = cred.password,
                    domain = cred.domain ?: "",
                    shareName = cred.shareName
                )
                
                // Check if exists to avoid duplicates (simplified check)
                val existing = if (typeStr == "SMB") {
                    credentialsRepository.getByServerAndShare(cred.server, cred.shareName ?: "")
                } else {
                    credentialsRepository.getByTypeServerAndPort(typeStr, cred.server, entity.port)
                }
                
                if (existing == null) {
                    credentialsRepository.insert(entity)
                    log("Inserted credential for ${cred.type}://${cred.server}")
                } else {
                    // Always update to ensure test credentials are current
                    credentialsRepository.update(entity.copy(id = existing.id, credentialId = existing.credentialId))
                    log("Updated credential for ${cred.type}://${cred.server}")
                }
            }
        } catch (e: Exception) {
            log("ERROR: Failed to initialize test credentials: ${e.message}")
            Timber.e(e, "Failed to inject test credentials")
        }
    }

    /**
     * Find a resource by its exact name in the database.
     * @return The MediaResource if found, null otherwise.
     */
    private suspend fun findResourceByName(name: String): com.sza.fastmediasorter.domain.model.MediaResource? {
        return try {
            val resources = resourceRepository.getFilteredResources(
                filterByType = null,
                filterByMediaType = null,
                filterByName = name,
                sortMode = com.sza.fastmediasorter.domain.model.SortMode.NAME_ASC
            )
            resources.firstOrNull { it.name.equals(name, ignoreCase = true) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to find resource by name: $name")
            null
        }
    }
    
    /**
     * Find a cloud resource by cloud provider type.
     * Uses predefined naming conventions for test resources (as documented in sza_resources.xml).
     */
    private suspend fun findCloudResource(provider: CloudProvider): com.sza.fastmediasorter.domain.model.MediaResource? {
        val name = cloudTestResourceName(provider)
        return findResourceByName(name)
    }

    private fun cloudTestResourceName(provider: CloudProvider): String {
        return when (provider) {
            CloudProvider.ONEDRIVE -> "onedrive_test"
            CloudProvider.GOOGLE_DRIVE -> "google_drive_test"
            CloudProvider.DROPBOX -> "dropbox_test"
        }
    }

    private fun cloudProviderPathSegment(provider: CloudProvider): String {
        return when (provider) {
            CloudProvider.ONEDRIVE -> "onedrive"
            CloudProvider.GOOGLE_DRIVE -> "google_drive"
            CloudProvider.DROPBOX -> "dropbox"
        }
    }

    private suspend fun ensureCloudTestResources() {
        try {
            for (provider in CloudProvider.values()) {
                ensureCloudResourceForProvider(provider)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure cloud test resources")
            log("WARNING: Failed to ensure cloud test resources: ${e.message}")
        }
    }

    private suspend fun ensureCloudResourceForProvider(provider: CloudProvider): Boolean {
        val existing = findCloudResource(provider)
        if (existing != null) {
            return true
        }

        val cloudCredential = credentialsLoader.getCloudCredential(provider)
        if (cloudCredential == null) {
            log("[Cloud Setup] ${provider.name}: no cloud credential found, resource not auto-created")
            return false
        }

        val folderIdOrPath = cloudCredential.folder
            ?.trim()
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }

        if (folderIdOrPath == null) {
            log("[Cloud Setup] ${provider.name}: credential folder is empty, resource not auto-created")
            return false
        }

        val resourceName = cloudTestResourceName(provider)
        val pathPrefix = cloudProviderPathSegment(provider)
        val cloudPath = "cloud://$pathPrefix/$folderIdOrPath"

        val resourceId = resourceRepository.addResource(
            com.sza.fastmediasorter.domain.model.MediaResource(
                name = resourceName,
                path = cloudPath,
                type = ResourceType.CLOUD,
                cloudProvider = provider,
                cloudFolderId = folderIdOrPath,
                supportedMediaTypes = setOf(
                    MediaType.IMAGE,
                    MediaType.VIDEO,
                    MediaType.AUDIO,
                    MediaType.GIF,
                    MediaType.TEXT,
                    MediaType.PDF
                )
            )
        )

        log("[Cloud Setup] Created $resourceName (id=$resourceId, path=$cloudPath)")
        return true
    }
    
    /**
     * Find the SMB test resource by name (matches sza_resources.xml).
     */
    private suspend fun findSmbResource(): com.sza.fastmediasorter.domain.model.MediaResource? {
        // "test_media" is defined in sza_resources.xml
        return findResourceByName("test_media")
    }

    /**
     * Find the SFTP test resource by name (matches sza_resources.xml).
     */
    private suspend fun findSftpResource(): com.sza.fastmediasorter.domain.model.MediaResource? {
        // "SFTP" is defined in sza_resources.xml
        return findResourceByName("SFTP")
    }

    /**
     * Find the FTP test resource by name (matches sza_resources.xml).
     */
    private suspend fun findFtpResource(): com.sza.fastmediasorter.domain.model.MediaResource? {
        // "FTP" is defined in sza_resources.xml
        return findResourceByName("FTP")
    }
    
    // Store last test results for failed-only reruns
    private val lastFailedTests = mutableSetOf<String>()
    
    // SharedPreferences for persisting failed tests between app restarts
    private val prefs = context.getSharedPreferences("integration_tests", Context.MODE_PRIVATE)
    
    init {
        // Load failed tests from previous run
        // val savedFailedTests = prefs.getStringSet("failed_tests", null)
        // if (savedFailedTests != null) {
        //     lastFailedTests.addAll(savedFailedTests)
        // }

        // HARDCODED FAILED TESTS (As per user request 2026-01-19)
        lastFailedTests.clear()
        lastFailedTests.addAll(listOf(
            "Copy Local → SMB",
            "Copy SMB → Local",
            "Copy SMB → SFTP",
            "Copy FTP → SMB",
            "SMB Delete",
            "Matrix Copy: LOCAL → SMB",
            "Matrix Copy: LOCAL → CLOUD",
            "Matrix Copy: SMB → LOCAL",
            "Matrix Copy: SMB → SMB",
            "Matrix Copy: SMB → SFTP",
            "Matrix Copy: SMB → FTP",
            "Matrix Copy: SMB → CLOUD",
            "Matrix Copy: SFTP → SMB",
            "Matrix Copy: SFTP → CLOUD",
            "Matrix Copy: FTP → SMB",
            "Matrix Copy: FTP → CLOUD",
            "Matrix Copy: CLOUD → LOCAL",
            "Matrix Copy: CLOUD → SMB",
            "Matrix Copy: CLOUD → SFTP",
            "Matrix Copy: CLOUD → FTP",
            "Matrix Copy: CLOUD → CLOUD",
            "Matrix Move: LOCAL → SMB",
            "Matrix Move: LOCAL → CLOUD",
            "Matrix Move: SMB → LOCAL",
            "Matrix Move: SMB → SMB",
            "Matrix Move: SMB → SFTP",
            "Matrix Move: SMB → FTP",
            "Matrix Move: SMB → CLOUD",
            "Matrix Move: SFTP → SMB",
            "Matrix Move: SFTP → CLOUD",
            "Matrix Move: FTP → SMB",
            "Matrix Move: FTP → CLOUD",
            "Matrix Move: CLOUD → LOCAL",
            "Matrix Move: CLOUD → SMB",
            "Matrix Move: CLOUD → SFTP",
            "Matrix Move: CLOUD → FTP",
            "Matrix Move: CLOUD → CLOUD",
            "Matrix Rename: SMB",
            "Matrix Rename: CLOUD",
            "Matrix Delete: SMB",
            "Matrix Delete: CLOUD",
            "GIF Extract Frames",
            "GIF Save First Frame",
            "Settings Export",
            "Settings Round-trip",
            "Video Metadata Extract",
            "ONEDRIVE Download",
            "ONEDRIVE Copy",
            "ONEDRIVE Rename",
            "ONEDRIVE Delete",
            "GOOGLE_DRIVE Copy",
            "GOOGLE_DRIVE Rename",
            "Cover Art Search",
            "SMB Speed Test",
            "SMB Download",
            "SFTP Download",
            "FTP Download",
            "SMB Image Edit"
        ))
    }

    /**
     * Manually set the list of failed tests (e.g., from imported log).
     */
    fun setFailedTests(testNames: List<String>) {
        lastFailedTests.clear()
        lastFailedTests.addAll(testNames)
        // Also save to prefs so it persists
        prefs.edit().putStringSet("failed_tests", lastFailedTests).apply()
        log("Manually set ${lastFailedTests.size} failed tests from import")
    }
    
    /**
     * Run all integration tests and emit progress.
     */
    fun runAllTests(): Flow<TestProgress> = runTestGroup(TestGroup.ALL)
    
    /**
     * Run a specific group of tests and emit progress.
     */
    fun runTestGroup(group: TestGroup): Flow<TestProgress> = flow {
        logBuilder.clear()
        testResults.clear()
        
        log("=".repeat(80))
        log("INTEGRATION TEST SUITE - ${group.displayName}")
        log("Time: ${getCurrentTimestamp()}")
        log("=".repeat(80))
        log("")

        // Initialize credentials before running any tests
        initializeTestCredentials()
        ensureCloudTestResources()
        
        val tests = buildTestList(group)
        var currentTest = 0
        
        for (test in tests) {
            currentTest++
            emit(TestProgress(currentTest, tests.size, test.name, "Running..."))
            
            try {
                test.execute()
            } catch (e: Exception) {
                log("ERROR: Unexpected exception in test ${test.name}: ${e.message}")
                Timber.e(e, "Test execution failed")
            }
            
            emit(TestProgress(currentTest, tests.size, test.name, "Complete"))
        }
        
        generateFinalReport()
        
        emit(TestProgress(tests.size, tests.size, "All tests complete", "Done"))
    }
    
    /**
     * Get the complete test log.
     */
    fun getTestLog(): String = logBuilder.toString()
    
    /**
     * Get test results summary.
     */
    fun getTestResults(): List<TestResult> = testResults.toList()
    
    private suspend fun buildTestList(group: TestGroup = TestGroup.ALL): List<Test> {
        val tests = mutableListOf<Test>()
        
        // Use ALL group for collection if we are in FAILED_ONLY mode
        val collectionGroup = if (group == TestGroup.FAILED_ONLY) TestGroup.ALL else group
        
        // Special handling for FAILED_ONLY group - run only previously failed tests
        if (group == TestGroup.FAILED_ONLY) {
            if (lastFailedTests.isEmpty()) {
                log("No failed tests from previous run. Running all tests instead.")
                return buildTestList(TestGroup.ALL)
            }
            log("Running ${lastFailedTests.size} previously failed tests:")
            lastFailedTests.forEach { log("  - $it") }
            log("")
        }
        
        // Helper function to check if test should be included
        fun shouldIncludeTest(testName: String): Boolean {
            return group != TestGroup.FAILED_ONLY || testName in lastFailedTests
        }
        
        // 1. Local file operations (baseline)
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.LOCAL) {
            tests.add(Test("Local Copy") { testLocalCopy() })
            tests.add(Test("Local Move") { testLocalMove() })
            tests.add(Test("Local Rename") { testLocalRename() })
            tests.add(Test("Local Delete") { testLocalDelete() })
            tests.add(Test("Local Copy Partial Success") { testLocalCopyPartialSuccess() })
            tests.add(Test("Local Copy Overwrite Policy") { testLocalCopyOverwritePolicy() })
        }
        
        // 2-4. Network copy operations
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.NETWORK_COPY) {
            // 2. Local → Network operations
            tests.add(Test("Copy Local → SMB") { testCopyLocalToSmb() })
            tests.add(Test("Copy Local → SFTP") { testCopyLocalToSftp() })
            tests.add(Test("Copy Local → FTP") { testCopyLocalToFtp() })
            
            // 3. Network → Local operations
            tests.add(Test("Copy SMB → Local") { testCopySmbToLocal() })
            tests.add(Test("Copy SFTP → Local") { testCopySftpToLocal() })
            tests.add(Test("Copy FTP → Local") { testCopyFtpToLocal() })
            
            // 4. Network → Network operations
            tests.add(Test("Copy SMB → SFTP") { testCopySmbToSftp() })
            tests.add(Test("Copy SFTP → FTP") { testCopySftpToFtp() })
            tests.add(Test("Copy FTP → SMB") { testCopyFtpToSmb() })
            
            // 6. Network file operations
            tests.add(Test("SMB Rename") { testSmbRename() })
            tests.add(Test("SMB Delete") { testSmbDelete() })
            tests.add(Test("SFTP Rename") { testSftpRename() })
            tests.add(Test("SFTP Delete") { testSftpDelete() })
        }
        
        // 5. Cloud storage operations - DEPRECATED (use CLOUD_PROVIDERS instead)
        // Old generic tests removed - all cloud tests moved to CLOUD_PROVIDERS group
        
        // ========== N×N MATRIX TESTS ==========
        val resourcePairs = generateResourcePairs()
        val availableTypes = getAvailableResourceTypes()
        
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.MATRIX_COPY || collectionGroup == TestGroup.MATRIX_MOVE || collectionGroup == TestGroup.MATRIX_OTHER) {
            log("Available resource types for N×N tests: ${availableTypes.joinToString { it.name }}")
            log("Total resource pairs to test: ${resourcePairs.size}")
        }
        
        // 7. N×N Copy Matrix Tests
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.MATRIX_COPY) {
            for ((sourceType, destType) in resourcePairs) {
                tests.add(Test("Copy ${sourceType.name} → ${destType.name}") {
                    testMatrixCopy(sourceType, destType)
                })
            }
        }
        
        // 8. N×N Move Matrix Tests
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.MATRIX_MOVE) {
            for ((sourceType, destType) in resourcePairs) {
                tests.add(Test("Move ${sourceType.name} → ${destType.name}") {
                    testMatrixMove(sourceType, destType)
                })
            }
        }
        
        // 9-10. Rename and Delete Tests for Each Protocol
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.MATRIX_OTHER) {
            // 9. Rename Tests for Each Protocol
            for (resourceType in availableTypes) {
                tests.add(Test("Matrix Rename: ${resourceType.name}") {
                    testMatrixRename(resourceType)
                })
            }
            
            // 10. Delete Tests for Each Protocol
            for (resourceType in availableTypes) {
                tests.add(Test("Matrix Delete: ${resourceType.name}") {
                    testMatrixDelete(resourceType)
                })
            }

            // 11. Delete path policy validation (guards soft-delete routing)
            tests.add(Test("Delete Policy Matrix") {
                testDeletePolicyMatrix()
            })

            // 12. Delete policy check per resource type
            for (resourceType in availableTypes) {
                tests.add(Test("Delete Policy: ${resourceType.name}") {
                    testDeletePolicyByResourceType(resourceType)
                })
            }

            // 13. Explicit Android/media guard
            tests.add(Test("Delete Policy: Android/media Guard") {
                testDeletePolicyAndroidMediaGuard()
            })
        }
        
        // ========== IMAGE EDIT TESTS (Phase 2) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.IMAGE) {
            // 11. Rotate Tests (90, 180, 270 degrees)
            tests.add(Test("Image Rotate 90°") { testImageRotate(90f) })
            tests.add(Test("Image Rotate 180°") { testImageRotate(180f) })
            tests.add(Test("Image Rotate 270°") { testImageRotate(270f) })
            
            // 12. Flip Tests
            tests.add(Test("Image Flip HORIZONTAL") { 
                testImageFlip(FlipImageUseCase.FlipDirection.HORIZONTAL) 
            })
            tests.add(Test("Image Flip VERTICAL") { 
                testImageFlip(FlipImageUseCase.FlipDirection.VERTICAL) 
            })
            
            // 13. Filter Tests
            tests.add(Test("Image Filter GRAYSCALE") { 
                testImageFilter(ApplyImageFilterUseCase.FilterType.GRAYSCALE) 
            })
            tests.add(Test("Image Filter SEPIA") { 
                testImageFilter(ApplyImageFilterUseCase.FilterType.SEPIA) 
            })
            tests.add(Test("Image Filter NEGATIVE") { 
                testImageFilter(ApplyImageFilterUseCase.FilterType.NEGATIVE) 
            })
            
            // 14. Adjust Tests
            tests.add(Test("Image Adjust Brightness") { 
                testImageAdjust(AdjustImageUseCase.Adjustments(brightness = 50f)) 
            })
            tests.add(Test("Image Adjust Contrast") { 
                testImageAdjust(AdjustImageUseCase.Adjustments(contrast = 1.5f)) 
            })
            tests.add(Test("Image Adjust Saturation") { 
                testImageAdjust(AdjustImageUseCase.Adjustments(saturation = 1.5f)) 
            })
        }
        
        // ========== GIF TESTS (Phase 3) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.GIF) {
            tests.add(Test("GIF Extract Frames") { testGifExtractFrames() })
            tests.add(Test("GIF Save First Frame") { testGifSaveFirstFrame() })
            tests.add(Test("GIF Speed 2.0x") { testGifChangeSpeed(2.0f) })
            tests.add(Test("GIF Speed 0.5x") { testGifChangeSpeed(0.5f) })
        }
        
        // ========== SETTINGS TESTS (Module 1) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.SETTINGS) {
            tests.add(Test("Settings Export") { testSettingsExport() })
            tests.add(Test("Settings Import") { testSettingsImport() })
            tests.add(Test("Settings Round-trip") { testSettingsRoundTrip() })
        }
        
        // ========== FAVORITES TESTS (Module 2) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.FAVORITES) {
            tests.add(Test("Favorites Add") { testFavoritesAdd() })
            tests.add(Test("Favorites Remove") { testFavoritesRemove() })
            tests.add(Test("Favorites Toggle") { testFavoritesToggle() })
            tests.add(Test("Favorites List") { testFavoritesList() })
        }
        
        // ========== METADATA TESTS (Module 3) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.METADATA) {
            tests.add(Test("EXIF Metadata Extract") { testExtractExifMetadata() })
            tests.add(Test("Video Metadata Extract") { testExtractVideoMetadata() })
        }
        
        // ========== CLOUD PROVIDER TESTS (Module 6) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.CLOUD_PROVIDERS) {
            // OneDrive tests
            tests.add(Test("ONEDRIVE Upload") { testCloudProviderUpload(CloudProvider.ONEDRIVE) })
            tests.add(Test("ONEDRIVE Download") { testCloudProviderDownload(CloudProvider.ONEDRIVE) })
            tests.add(Test("ONEDRIVE Copy") { testCloudProviderCopy(CloudProvider.ONEDRIVE) })
            tests.add(Test("ONEDRIVE Rename") { testCloudProviderRename(CloudProvider.ONEDRIVE) })
            tests.add(Test("ONEDRIVE Delete") { testCloudProviderDelete(CloudProvider.ONEDRIVE) })
            
            // Dropbox tests
            tests.add(Test("DROPBOX Upload") { testCloudProviderUpload(CloudProvider.DROPBOX) })
            tests.add(Test("DROPBOX Download") { testCloudProviderDownload(CloudProvider.DROPBOX) })
            tests.add(Test("DROPBOX Copy") { testCloudProviderCopy(CloudProvider.DROPBOX) })
            tests.add(Test("DROPBOX Rename") { testCloudProviderRename(CloudProvider.DROPBOX) })
            tests.add(Test("DROPBOX Delete") { testCloudProviderDelete(CloudProvider.DROPBOX) })
            
            // Google Drive tests
            tests.add(Test("GOOGLE_DRIVE Upload") { testCloudProviderUpload(CloudProvider.GOOGLE_DRIVE) })
            tests.add(Test("GOOGLE_DRIVE Download") { testCloudProviderDownload(CloudProvider.GOOGLE_DRIVE) })
            tests.add(Test("GOOGLE_DRIVE Copy") { testCloudProviderCopy(CloudProvider.GOOGLE_DRIVE) })
            tests.add(Test("GOOGLE_DRIVE Rename") { testCloudProviderRename(CloudProvider.GOOGLE_DRIVE) })
            tests.add(Test("GOOGLE_DRIVE Delete") { testCloudProviderDelete(CloudProvider.GOOGLE_DRIVE) })

            // Functional cloud move tests (path-based and cross-provider fallback)
            tests.add(Test("Cloud Move Same Provider Path") { testCloudMoveSameProviderPathBased() })
            tests.add(Test("Cloud Move Cross Provider") { testCloudMoveCrossProvider() })
        }
        
        // ========== AUDIO TESTS (Module 7) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.AUDIO) {
            tests.add(Test("Lyrics Search") { testSearchLyrics() })
            tests.add(Test("Cover Art Search") { testSearchCoverArt() })
        }
        
        // ========== NETWORK UTILITY TESTS (Module 8) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.NETWORK_UTILS) {
            tests.add(Test("Network Discovery") { testNetworkDiscovery() })
            tests.add(Test("SMB Speed Test") { testNetworkSpeedTest(com.sza.fastmediasorter.domain.model.ResourceType.SMB) })
            tests.add(Test("SMB Download") { testNetworkDownload("SMB") })
            tests.add(Test("SFTP Download") { testNetworkDownload("SFTP") })
            tests.add(Test("FTP Download") { testNetworkDownload("FTP") })
            tests.add(Test("SMB Image Edit") { testNetworkImageEdit("SMB") })
        }
        
        // ========== CACHE TESTS (Module 9) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.CACHE) {
            tests.add(Test("Calculate Optimal Cache Size") { testCalculateOptimalCacheSize() })
        }
        
        // ========== SCANNING TESTS (Module 10) ==========
        if (collectionGroup == TestGroup.ALL || collectionGroup == TestGroup.SCANNING) {
            tests.add(Test("Local Folder Scan") { testScanLocalFolders() })
        }
        
        // Filter tests for FAILED_ONLY group
        return if (group == TestGroup.FAILED_ONLY) {
            tests.filter { it.name in lastFailedTests }
        } else {
            tests
        }
    }
    
    // ========== LOCAL OPERATIONS ==========
    
    private suspend fun testLocalCopy() {
        val testName = "Local Copy"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val sourceFile = createTestFile("local_copy_source.txt", "Test content for local copy")
            val destDir = File(context.cacheDir, "test_dest")
            destDir.mkdirs()
            
            val operation = FileOperation.Copy(
                sources = listOf(sourceFile),
                destination = destDir,
                overwrite = false
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                val destFile = File(destDir, sourceFile.name)
                val success = destFile.exists() && destFile.readText() == "Test content for local copy"
                
                recordResult(testName, "Copy", "Local", "Local", success, duration,
                    details = "Copied ${sourceFile.name} to ${destDir.absolutePath}")
                
                // Cleanup
                sourceFile.delete()
                destFile.delete()
                destDir.delete()
            } else {
                recordResult(testName, "Copy", "Local", "Local", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
        } catch (e: Exception) {
            recordResult(testName, "Copy", "Local", "Local", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testLocalMove() {
        val testName = "Local Move"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val sourceFile = createTestFile("local_move_source.txt", "Test content for local move")
            val destDir = File(context.cacheDir, "test_dest")
            destDir.mkdirs()
            
            val operation = FileOperation.Move(
                sources = listOf(sourceFile),
                destination = destDir,
                overwrite = false
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                val destFile = File(destDir, "local_move_source.txt")
                val success = destFile.exists() && !sourceFile.exists()
                
                recordResult(testName, "Move", "Local", "Local", success, duration,
                    details = "Moved file to ${destDir.absolutePath}")
                
                // Cleanup
                destFile.delete()
                destDir.delete()
            } else {
                recordResult(testName, "Move", "Local", "Local", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
        } catch (e: Exception) {
            recordResult(testName, "Move", "Local", "Local", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testLocalRename() {
        val testName = "Local Rename"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val sourceFile = createTestFile("old_name.txt", "Test content")
            val newName = "new_name.txt"
            
            val operation = FileOperation.Rename(
                file = sourceFile,
                newName = newName
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                val renamedFile = File(sourceFile.parent, newName)
                val success = renamedFile.exists() && !sourceFile.exists()
                
                recordResult(testName, "Rename", "Local", null, success, duration,
                    details = "Renamed to $newName")
                
                // Cleanup
                renamedFile.delete()
            } else {
                recordResult(testName, "Rename", "Local", null, false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
        } catch (e: Exception) {
            recordResult(testName, "Rename", "Local", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testLocalDelete() {
        val testName = "Local Delete"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val fileToDelete = createTestFile("delete_me.txt", "Delete this")
            
            val operation = FileOperation.Delete(
                files = listOf(fileToDelete),
                softDelete = false
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                val success = !fileToDelete.exists()
                
                recordResult(testName, "Delete", "Local", null, success, duration,
                    details = "File deleted successfully")
            } else {
                recordResult(testName, "Delete", "Local", null, false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
        } catch (e: Exception) {
            recordResult(testName, "Delete", "Local", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }

    private suspend fun testLocalCopyPartialSuccess() {
        val testName = "Local Copy Partial Success"
        val startTime = System.currentTimeMillis()

        log("[$testName] Starting...")

        val destinationDir = File(context.cacheDir, "test_local_partial_dest_${System.currentTimeMillis()}")
        val existingSource = File(context.cacheDir, "test_local_partial_ok_${System.currentTimeMillis()}.txt")
        val missingSource = File(context.cacheDir, "test_local_partial_missing_${System.currentTimeMillis()}.txt")

        try {
            destinationDir.mkdirs()
            existingSource.writeText("partial success content")

            val result = fileOperationUseCase.execute(
                FileOperation.Copy(
                    sources = listOf(existingSource, missingSource),
                    destination = destinationDir,
                    overwrite = true
                )
            )

            val duration = System.currentTimeMillis() - startTime
            val copiedFile = File(destinationDir, existingSource.name)

            val success = when (result) {
                is FileOperationResult.PartialSuccess -> {
                    result.processedCount == 1 && result.failedCount >= 1 && copiedFile.exists()
                }
                else -> false
            }

            recordResult(
                testName,
                "Copy",
                "LOCAL",
                "LOCAL",
                success,
                duration,
                error = if (success) null else "Expected PartialSuccess with 1 copied and >=1 failed, got: ${result::class.simpleName}",
                details = if (success) "PartialSuccess validated: copied file exists, missing file failed" else null
            )
        } catch (e: Exception) {
            recordResult(
                testName,
                "Copy",
                "LOCAL",
                "LOCAL",
                false,
                System.currentTimeMillis() - startTime,
                error = e.message
            )
        } finally {
            if (existingSource.exists()) existingSource.delete()
            if (missingSource.exists()) missingSource.delete()
            destinationDir.listFiles()?.forEach { it.delete() }
            if (destinationDir.exists()) destinationDir.delete()
        }
    }

    private suspend fun testLocalCopyOverwritePolicy() {
        val testName = "Local Copy Overwrite Policy"
        val startTime = System.currentTimeMillis()

        log("[$testName] Starting...")

        val destinationDir = File(context.cacheDir, "test_local_overwrite_dest_${System.currentTimeMillis()}")
        val sourceFile = File(context.cacheDir, "test_overwrite_source.txt")
        val destinationFile = File(destinationDir, sourceFile.name)

        try {
            destinationDir.mkdirs()
            sourceFile.writeText("new-content")
            destinationFile.writeText("old-content")

            val noOverwriteResult = fileOperationUseCase.execute(
                FileOperation.Copy(
                    sources = listOf(sourceFile),
                    destination = destinationDir,
                    overwrite = false
                )
            )

            val noOverwriteRespected = destinationFile.readText() == "old-content"

            val overwriteResult = fileOperationUseCase.execute(
                FileOperation.Copy(
                    sources = listOf(sourceFile),
                    destination = destinationDir,
                    overwrite = true
                )
            )

            val overwriteApplied = destinationFile.exists() && destinationFile.readText() == "new-content"
            val overwriteCallSucceeded = overwriteResult is FileOperationResult.Success ||
                (overwriteResult is FileOperationResult.PartialSuccess && overwriteResult.processedCount > 0)

            val success = noOverwriteRespected && overwriteApplied && overwriteCallSucceeded

            recordResult(
                testName,
                "Copy",
                "LOCAL",
                "LOCAL",
                success,
                System.currentTimeMillis() - startTime,
                error = if (success) null else "Overwrite policy behavior mismatch. noOverwrite=${noOverwriteResult::class.simpleName}, overwrite=${overwriteResult::class.simpleName}",
                details = if (success) "overwrite=false preserved old content; overwrite=true replaced with new content" else null
            )
        } catch (e: Exception) {
            recordResult(
                testName,
                "Copy",
                "LOCAL",
                "LOCAL",
                false,
                System.currentTimeMillis() - startTime,
                error = e.message
            )
        } finally {
            if (sourceFile.exists()) sourceFile.delete()
            destinationDir.listFiles()?.forEach { it.delete() }
            if (destinationDir.exists()) destinationDir.delete()
        }
    }
    
    // ========== NETWORK OPERATIONS ==========
    
    private suspend fun testCopyLocalToSmb() {
        val testName = "Copy Local → SMB"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Find SMB test resource from database
            val smbResource = findSmbResource()
            if (smbResource == null) {
                recordResult(testName, "Copy", "Local", "SMB", false, 0,
                    error = "SMB Test Share not found in database")
                return
            }
            
            // Create local test file
            val localFile = createTestFile("test_smb_upload.txt", "SMB upload test content")
            
            // Create SMB destination path using resource path
            val smbPath = "${smbResource.path.trimEnd('/')}/test_integration"
            val smbDestDir = File(smbPath)
            
            // Execute copy operation
            val operation = FileOperation.Copy(
                sources = listOf(localFile),
                destination = smbDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Copy", "Local", "SMB", true, duration,
                    details = "Uploaded to ${smbResource.name} at $smbPath")
            } else {
                recordResult(testName, "Copy", "Local", "SMB", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
            // Cleanup
            localFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "Local", "SMB", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopyLocalToSftp() {
        val testName = "Copy Local → SFTP"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Find SFTP test resource from database
            val sftpResource = findSftpResource()
            if (sftpResource == null) {
                recordResult(testName, "Copy", "Local", "SFTP", false, 0,
                    error = "SFTP resource not found in database")
                return
            }
            
            val localFile = createTestFile("test_sftp_upload.txt", "SFTP upload test")
            
            // Build destination path
            val sftpPath = "${sftpResource.path.trimEnd('/')}/test_integration"
            val sftpDestDir = File(sftpPath)
            
            val operation = FileOperation.Copy(
                sources = listOf(localFile),
                destination = sftpDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Copy", "Local", "SFTP", true, duration,
                    details = "Uploaded to ${sftpResource.name}")
            } else {
                recordResult(testName, "Copy", "Local", "SFTP", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
            localFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "Local", "SFTP", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopyLocalToFtp() {
        val testName = "Copy Local → FTP"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Find FTP test resource from database
            val ftpResource = findFtpResource()
            if (ftpResource == null) {
                recordResult(testName, "Copy", "Local", "FTP", false, 0,
                    error = "FTP resource not found in database")
                return
            }

            val localFile = createTestFile("test_ftp_upload.txt", "FTP upload test")
            
            // Build destination path
            val ftpPath = "${ftpResource.path.trimEnd('/')}/test_integration"
            val ftpDestDir = File(ftpPath)
            
            val operation = FileOperation.Copy(
                sources = listOf(localFile),
                destination = ftpDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Copy", "Local", "FTP", true, duration,
                    details = "Uploaded to ${ftpResource.name}")
            } else {
                recordResult(testName, "Copy", "Local", "FTP", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
            localFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "Local", "FTP", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopySmbToLocal() {
        val testName = "Copy SMB → Local"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val smbCred = credentialsLoader.getCredential(ResourceType.SMB)
            if (smbCred == null) {
                recordResult(testName, "Copy", "SMB", "Local", false, 0,
                    error = "SMB credentials not found")
                return
            }
            
            // 1. Ensure source file exists on SMB
            val smbPath = "smb://${smbCred.server}/${smbCred.shareName ?: ""}/test_integration/test_smb_upload.txt"
            val smbFile = File(smbPath)
            
            log("[$testName] Ensuring source exists at: $smbPath")
            
            val tempUploadFile = createTestFile("temp_upload_for_smb_dl.txt", "Content for SMB download test")
            val uploadOp = FileOperation.Copy(
                sources = listOf(tempUploadFile),
                destination = File(smbFile.parent!!),
                overwrite = true
            )
            val uploadResult = fileOperationUseCase.execute(uploadOp)
            if (uploadResult !is FileOperationResult.Success) {
                 recordResult(testName, "Copy", "SMB", "Local", false, 0,
                    error = "Setup failed: Could not upload source file to SMB: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                 tempUploadFile.delete()
                 return
            }
            tempUploadFile.delete()

            // 2. Perform Download Test
            val localDestDir = File(context.cacheDir, "test_smb_download")
            localDestDir.mkdirs()
            
            val operation = FileOperation.Copy(
                sources = listOf(smbFile),
                destination = localDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                val downloadedFile = File(localDestDir, "test_smb_upload.txt")
                val success = downloadedFile.exists()
                
                recordResult(testName, "Copy", "SMB", "Local", success, duration,
                    details = "Downloaded from ${smbCred.server}")
                
                downloadedFile.delete()
                localDestDir.delete()
            } else {
                recordResult(testName, "Copy", "SMB", "Local", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "SMB", "Local", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopySftpToLocal() {
        val testName = "Copy SFTP → Local"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val credential = credentialsLoader.getCredential(ResourceType.SFTP)
            if (credential == null) {
                recordResult(testName, "Copy", "SFTP", "Local", false, 0,
                    error = "SFTP credentials not found")
                return
            }
            
            // 1. Ensure source file exists on SFTP
            val folderPath = normalizedRemoteFolderPrefix(credential.folder)
            // Clean path: sftp://host:port/...
            val sftpPath = "sftp://${credential.server}:${credential.port}${folderPath}/test_integration/test_sftp_upload.txt"
            val sftpFile = File(sftpPath)
            
            log("[$testName] Ensuring source exists at: $sftpPath")
            
            val tempUploadFile = createTestFile("test_sftp_upload.txt", "Content for SFTP download test")
            val uploadOp = FileOperation.Copy(
                sources = listOf(tempUploadFile),
                destination = File(sftpFile.parent!!),
                overwrite = true
            )
            val uploadResult = fileOperationUseCase.execute(uploadOp)
            if (uploadResult !is FileOperationResult.Success) {
                 recordResult(testName, "Copy", "SFTP", "Local", false, 0,
                    error = "Setup failed: Could not upload source file to SFTP: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                 tempUploadFile.delete()
                 return
            }
            tempUploadFile.delete()
            
            // 2. Perform Download Test
            val localDestDir = File(context.cacheDir, "test_sftp_download")
            localDestDir.mkdirs()
            
            val operation = FileOperation.Copy(
                sources = listOf(sftpFile),
                destination = localDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                val downloadedFile = File(localDestDir, "test_sftp_upload.txt")
                val success = downloadedFile.exists()
                
                recordResult(testName, "Copy", "SFTP", "Local", success, duration,
                    details = "Downloaded from ${credential.server}")
                
                downloadedFile.delete()
                localDestDir.delete()
            } else {
                recordResult(testName, "Copy", "SFTP", "Local", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "SFTP", "Local", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopyFtpToLocal() {
        val testName = "Copy FTP → Local"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val credential = credentialsLoader.getCredential(ResourceType.FTP)
            if (credential == null) {
                recordResult(testName, "Copy", "FTP", "Local", false, 0,
                    error = "FTP credentials not found")
                return
            }
            
            // 1. Ensure source file exists
            val folderPath = normalizedRemoteFolderPrefix(credential.folder)
            // Clean path: ftp://host:port/...
            val ftpPath = "ftp://${credential.server}:${credential.port}${folderPath}/test_integration/test_ftp_upload.txt"
            val ftpFile = File(ftpPath)
            
            log("[$testName] Ensuring source exists at: $ftpPath")

            val tempUploadFile = createTestFile("temp_upload_for_ftp_dl.txt", "Content for FTP download test")
            val uploadOp = FileOperation.Copy(
                sources = listOf(tempUploadFile),
                destination = File(ftpFile.parent!!),
                overwrite = true
            )
            val uploadResult = fileOperationUseCase.execute(uploadOp)
            if (uploadResult !is FileOperationResult.Success) {
                 recordResult(testName, "Copy", "FTP", "Local", false, 0,
                    error = "Setup failed: Could not upload source file to FTP: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                 tempUploadFile.delete()
                 return
            }
            tempUploadFile.delete()
            
            // 2. Perform Download Test
            val localDestDir = File(context.cacheDir, "test_ftp_download")
            localDestDir.mkdirs()
            
            val operation = FileOperation.Copy(
                sources = listOf(ftpFile),
                destination = localDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                val downloadedFile = File(localDestDir, "test_ftp_upload.txt")
                val success = downloadedFile.exists()
                
                recordResult(testName, "Copy", "FTP", "Local", success, duration,
                    details = "Downloaded from ${credential.server}")
                
                downloadedFile.delete()
                localDestDir.delete()
            } else {
                recordResult(testName, "Copy", "FTP", "Local", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "FTP", "Local", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopySmbToSftp() {
        val testName = "Copy SMB → SFTP"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val smbCred = credentialsLoader.getCredential(ResourceType.SMB)
            val sftpCred = credentialsLoader.getCredential(ResourceType.SFTP)
            
            if (smbCred == null || sftpCred == null) {
                recordResult(testName, "Copy", "SMB", "SFTP", false, 0,
                    error = "Missing credentials")
                return
            }
            
            // 1. Ensure source file exists on SMB
            val smbPath = "smb://${smbCred.server}/${smbCred.shareName ?: ""}/test_integration/test_smb_upload.txt"
            val smbFile = File(smbPath)
            
            log("[$testName] Ensuring source exists at: $smbPath")
            
            val tempUploadFile = createTestFile("temp_upload_for_smb_sftp.txt", "Content for SMB->SFTP copy test")
            val uploadOp = FileOperation.Copy(
                sources = listOf(tempUploadFile),
                destination = File(smbFile.parent!!),
                overwrite = true
            )
            val uploadResult = fileOperationUseCase.execute(uploadOp)
            if (uploadResult !is FileOperationResult.Success) {
                 recordResult(testName, "Copy", "SMB", "SFTP", false, 0,
                    error = "Setup failed: Could not upload source file to SMB: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                 tempUploadFile.delete()
                 return
            }
            tempUploadFile.delete()

            // 2. Perform Cross-Protocol Copy
            val folderPath = normalizedRemoteFolderPrefix(sftpCred.folder)
            // Clean path: sftp://host:port/...
            val sftpDestPath = "sftp://${sftpCred.server}:${sftpCred.port}${folderPath}/test_integration"
            val sftpDestDir = File(sftpDestPath)
            
            val operation = FileOperation.Copy(
                sources = listOf(smbFile),
                destination = sftpDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Copy", "SMB", "SFTP", true, duration,
                    details = "Copied from SMB to SFTP")
            } else {
                recordResult(testName, "Copy", "SMB", "SFTP", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "SMB", "SFTP", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopySftpToFtp() {
        val testName = "Copy SFTP → FTP"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val sftpCred = credentialsLoader.getCredential(ResourceType.SFTP)
            val ftpCred = credentialsLoader.getCredential(ResourceType.FTP)
            
            if (sftpCred == null || ftpCred == null) {
                recordResult(testName, "Copy", "SFTP", "FTP", false, 0,
                    error = "Missing credentials")
                return
            }
            
            // 1. Ensure source file exists on SFTP
            val sftpFolderPath = normalizedRemoteFolderPrefix(sftpCred.folder)
            // Clean path
            val sftpPath = "sftp://${sftpCred.server}:${sftpCred.port}${sftpFolderPath}/test_integration/test_sftp_upload.txt"
            val sftpFile = File(sftpPath)
            
            log("[$testName] Ensuring source exists at: $sftpPath")
            
            val tempUploadFile = createTestFile("test_sftp_upload.txt", "Content for SFTP->FTP copy test")
            val uploadOp = FileOperation.Copy(
                sources = listOf(tempUploadFile),
                destination = File(sftpFile.parent!!),
                overwrite = true
            )
            val uploadResult = fileOperationUseCase.execute(uploadOp)
            if (uploadResult !is FileOperationResult.Success) {
                 recordResult(testName, "Copy", "SFTP", "FTP", false, 0,
                    error = "Setup failed: Could not upload source file to SFTP: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                 tempUploadFile.delete()
                 return
            }
            tempUploadFile.delete()

            // 2. Perform Cross-Protocol Copy
            val ftpFolderPath = normalizedRemoteFolderPrefix(ftpCred.folder)
            // Clean path
            val ftpDestPath = "ftp://${ftpCred.server}:${ftpCred.port}${ftpFolderPath}/test_integration"
            val ftpDestDir = File(ftpDestPath)
            
            val operation = FileOperation.Copy(
                sources = listOf(sftpFile),
                destination = ftpDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Copy", "SFTP", "FTP", true, duration,
                    details = "Copied from SFTP to FTP")
            } else {
                recordResult(testName, "Copy", "SFTP", "FTP", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "SFTP", "FTP", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopyFtpToSmb() {
        val testName = "Copy FTP → SMB"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val ftpCred = credentialsLoader.getCredential(ResourceType.FTP)
            val smbCred = credentialsLoader.getCredential(ResourceType.SMB)
            
            if (ftpCred == null || smbCred == null) {
                recordResult(testName, "Copy", "FTP", "SMB", false, 0,
                    error = "Missing credentials")
                return
            }
            
            // 1. Ensure source file exists on FTP
            val folderPath = normalizedRemoteFolderPrefix(ftpCred.folder)
            // Clean path
            val ftpPath = "ftp://${ftpCred.server}:${ftpCred.port}${folderPath}/test_integration/test_ftp_upload.txt"
            val ftpFile = File(ftpPath)
            
            log("[$testName] Ensuring source exists at: $ftpPath")
            
            val tempUploadFile = createTestFile("temp_upload_for_ftp_smb.txt", "Content for FTP->SMB copy test")
            val uploadOp = FileOperation.Copy(
                sources = listOf(tempUploadFile),
                destination = File(ftpFile.parent!!),
                overwrite = true
            )
            val uploadResult = fileOperationUseCase.execute(uploadOp)
            if (uploadResult !is FileOperationResult.Success) {
                 recordResult(testName, "Copy", "FTP", "SMB", false, 0,
                    error = "Setup failed: Could not upload source file to FTP: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                 tempUploadFile.delete()
                 return
            }
            tempUploadFile.delete()

            // 2. Perform Cross-Protocol Copy
            val smbDestPath = "smb://${smbCred.server}/${smbCred.shareName ?: ""}/test_integration"
            val smbDestDir = File(smbDestPath)
            
            val operation = FileOperation.Copy(
                sources = listOf(ftpFile),
                destination = smbDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Copy", "FTP", "SMB", true, duration,
                    details = "Copied from FTP to SMB")
            } else {
                recordResult(testName, "Copy", "FTP", "SMB", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "FTP", "SMB", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testSmbRename() {
        val testName = "SMB Rename"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Find SMB test resource from database
            val smbResource = findSmbResource()
            if (smbResource == null) {
                recordResult(testName, "Rename", "SMB", null, false, 0,
                    error = "SMB Test Share not found in database")
                return
            }
            
            val smbFile = File("${smbResource.path.trimEnd('/')}/test_integration/test_smb_upload.txt")
            val newName = "test_smb_renamed.txt"
            
            val operation = FileOperation.Rename(
                file = smbFile,
                newName = newName
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Rename", "SMB", null, true, duration,
                    details = "Renamed to $newName on ${smbResource.name}")
                
                // Rename back for other tests
                val renamedFile = File("${smbResource.path.trimEnd('/')}/test_integration/$newName")
                fileOperationUseCase.execute(FileOperation.Rename(renamedFile, "test_smb_upload.txt"))
            } else {
                recordResult(testName, "Rename", "SMB", null, false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Rename", "SMB", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testSmbDelete() {
        val testName = "SMB Delete"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Find SMB test resource from database
            val smbResource = findSmbResource()
            if (smbResource == null) {
                recordResult(testName, "Delete", "SMB", null, false, 0,
                    error = "SMB Test Share not found in database")
                return
            }
            
            val smbFile = File("${smbResource.path.trimEnd('/')}/test_integration/test_smb_upload.txt")
            
            val operation = FileOperation.Delete(
                files = listOf(smbFile),
                softDelete = false
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Delete", "SMB", null, true, duration,
                    details = "Deleted from ${smbResource.name}")
            } else {
                recordResult(testName, "Delete", "SMB", null, false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Delete", "SMB", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testSftpRename() {
        val testName = "SFTP Rename"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val credential = credentialsLoader.getCredential(ResourceType.SFTP)
            if (credential == null) {
                recordResult(testName, "Rename", "SFTP", null, false, 0,
                    error = "SFTP credentials not found")
                return
            }
            
            val folderPath = normalizedRemoteFolderPrefix(credential.folder)
            val sftpFile = File(com.sza.fastmediasorter.utils.SftpPathUtils.buildSftpPath(
                host = credential.server,
                path = "${folderPath}/test_integration/test_sftp_upload.txt",
                port = credential.port ?: 22
            ))
            val newName = "test_sftp_renamed.txt"
            
            val operation = FileOperation.Rename(
                file = sftpFile,
                newName = newName
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Rename", "SFTP", null, true, duration,
                    details = "Renamed to $newName on SFTP")
                
                // Rename back
                val folderPath2 = normalizedRemoteFolderPrefix(credential.folder)
                val renamedFile = File(com.sza.fastmediasorter.utils.SftpPathUtils.buildSftpPath(
                    host = credential.server,
                    path = "${folderPath2}/test_integration/$newName",
                    port = credential.port ?: 22
                ))
                fileOperationUseCase.execute(FileOperation.Rename(renamedFile, "test_sftp_upload.txt"))
            } else {
                recordResult(testName, "Rename", "SFTP", null, false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Rename", "SFTP", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testSftpDelete() {
        val testName = "SFTP Delete"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val credential = credentialsLoader.getCredential(ResourceType.SFTP)
            if (credential == null) {
                recordResult(testName, "Delete", "SFTP", null, false, 0,
                    error = "SFTP credentials not found")
                return
            }
            
            val folderPath = normalizedRemoteFolderPrefix(credential.folder)
            val sftpFile = File(com.sza.fastmediasorter.utils.SftpPathUtils.buildSftpPath(
                host = credential.server,
                path = "${folderPath}/test_integration/test_sftp_upload.txt",
                port = credential.port ?: 22
            ))
            
            val operation = FileOperation.Delete(
                files = listOf(sftpFile),
                softDelete = false
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Delete", "SFTP", null, true, duration,
                    details = "Deleted from SFTP")
            } else {
                recordResult(testName, "Delete", "SFTP", null, false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Delete", "SFTP", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    // ========== CLOUD STORAGE OPERATIONS ==========
    
    private suspend fun testCopyLocalToCloud() {
        val testName = "Copy Local → Cloud"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val cloudResource = resourceRepository
                .getAllResourcesSync()
                .firstOrNull { it.type == ResourceType.CLOUD && it.path.startsWith("cloud://") }
            if (cloudResource == null) {
                recordResult(testName, "Copy", "Local", "Cloud", false, 0,
                    error = "Cloud resource not found in database")
                return
            }
            
            // Create local test file
            val localFile = createTestFile("test_cloud_upload.txt", "Cloud upload test content")
            
            val cloudDestDir = File("${cloudResource.path.trimEnd('/')}/test_integration")
            
            val operation = FileOperation.Copy(
                sources = listOf(localFile),
                destination = cloudDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Copy", "Local", "Cloud", true, duration,
                    details = "Uploaded to ${cloudResource.path}/test_integration")
            } else {
                recordResult(testName, "Copy", "Local", "Cloud", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
            // Cleanup
            localFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "Local", "Cloud", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCopyCloudToLocal() {
        val testName = "Copy Cloud → Local"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val cloudResource = resourceRepository
                .getAllResourcesSync()
                .firstOrNull { it.type == ResourceType.CLOUD && it.path.startsWith("cloud://") }
            if (cloudResource == null) {
                recordResult(testName, "Copy", "Cloud", "Local", false, 0,
                    error = "Cloud resource not found in database")
                return
            }
            
            // Assume test file exists from previous upload test
            val cloudFile = File("${cloudResource.path.trimEnd('/')}/test_integration/test_cloud_upload.txt")
            val localDestDir = File(context.cacheDir, "test_cloud_download")
            localDestDir.mkdirs()
            
            val operation = FileOperation.Copy(
                sources = listOf(cloudFile),
                destination = localDestDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                val downloadedFile = File(localDestDir, "test_cloud_upload.txt")
                val success = downloadedFile.exists()
                
                recordResult(testName, "Copy", "Cloud", "Local", success, duration,
                    details = "Downloaded from ${cloudResource.path}")
                
                // Cleanup
                downloadedFile.delete()
                localDestDir.delete()
            } else {
                recordResult(testName, "Copy", "Cloud", "Local", false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", "Cloud", "Local", false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCloudRename() {
        val testName = "Cloud Rename"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val cloudResource = resourceRepository
                .getAllResourcesSync()
                .firstOrNull { it.type == ResourceType.CLOUD && it.path.startsWith("cloud://") }
            if (cloudResource == null) {
                recordResult(testName, "Rename", "Cloud", null, false, 0,
                    error = "Cloud resource not found in database")
                return
            }
            
            val cloudBasePath = cloudResource.path.trimEnd('/')
            val cloudFile = File("$cloudBasePath/test_integration/test_cloud_upload.txt")
            val newName = "test_cloud_renamed.txt"
            
            val operation = FileOperation.Rename(
                file = cloudFile,
                newName = newName
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Rename", "Cloud", null, true, duration,
                    details = "Renamed to $newName on ${cloudResource.path}")
                
                // Rename back for other tests
                val renamedFile = File("$cloudBasePath/test_integration/$newName")
                fileOperationUseCase.execute(FileOperation.Rename(renamedFile, "test_cloud_upload.txt"))
            } else {
                recordResult(testName, "Rename", "Cloud", null, false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Rename", "Cloud", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    private suspend fun testCloudDelete() {
        val testName = "Cloud Delete"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val cloudResource = resourceRepository
                .getAllResourcesSync()
                .firstOrNull { it.type == ResourceType.CLOUD && it.path.startsWith("cloud://") }
            if (cloudResource == null) {
                recordResult(testName, "Delete", "Cloud", null, false, 0,
                    error = "Cloud resource not found in database")
                return
            }
            
            val cloudFile = File("${cloudResource.path.trimEnd('/')}/test_integration/test_cloud_upload.txt")
            
            val operation = FileOperation.Delete(
                files = listOf(cloudFile),
                softDelete = false
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            if (result is FileOperationResult.Success) {
                recordResult(testName, "Delete", "Cloud", null, true, duration,
                    details = "Deleted from ${cloudResource.path}")
            } else {
                recordResult(testName, "Delete", "Cloud", null, false, duration,
                    error = (result as? FileOperationResult.Failure)?.error)
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Delete", "Cloud", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    // ========== N×N MATRIX TEST IMPLEMENTATIONS ==========
    
    /**
     * Matrix Copy Test: Copy file from source resource type to destination resource type.
     * Tests all N×N combinations of source → destination.
     */
    private suspend fun testMatrixCopy(sourceType: ResourceType, destType: ResourceType) {
        val testName = "Matrix Copy: ${sourceType.name} → ${destType.name}"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create unique test file name
            val timestamp = System.currentTimeMillis()
            val sourceFileName = "matrix_copy_src_${sourceType.name}_${timestamp}.txt"
            
            // Ensure source file exists
            val sourcePrepare = ensureTestFileExists(sourceType, sourceFileName)
            val sourceFile = sourcePrepare.file
            if (sourceFile == null) {
                recordResult(testName, "Copy", sourceType.name, destType.name, false, System.currentTimeMillis() - startTime,
                    error = sourcePrepare.error ?: "Failed to create/access source file on ${sourceType.name}")
                return
            }
            
            // Create destination directory
            val destDir = buildResourcePath(destType, "test_matrix_dest_$timestamp")
            if (destDir == null) {
                recordResult(testName, "Copy", sourceType.name, destType.name, false, 0,
                    error = "Failed to access destination on ${destType.name}")
                return
            }
            
            log("[$testName] Copying from $sourceFile to $destDir")
            
            // Execute copy operation
            val operation = FileOperation.Copy(
                sources = listOf(sourceFile),
                destination = destDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            when (result) {
                is FileOperationResult.Success -> {
                    recordResult(testName, "Copy", sourceType.name, destType.name, true, duration,
                        details = "Copied ${sourceFile.name} to ${destType.name}")
                    
                    // Cleanup: delete copied file
                    val copiedFile = File(destDir, sourceFile.name)
                    fileOperationUseCase.execute(FileOperation.Delete(
                        files = listOf(copiedFile),
                        softDelete = false
                    ))
                }
                is FileOperationResult.PartialSuccess -> {
                    recordResult(testName, "Copy", sourceType.name, destType.name, true, duration,
                        details = "Partial success: ${result.processedCount}/${result.failedCount + result.processedCount}")
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Copy", sourceType.name, destType.name, false, duration,
                        error = result.error)
                }
                is FileOperationResult.AuthenticationRequired -> {
                    recordResult(testName, "Copy", sourceType.name, destType.name, false, duration,
                        error = "Authentication required for ${result.provider}: ${result.message}")
                }
                is FileOperationResult.PermissionRequired -> {
                    recordResult(testName, "Copy", sourceType.name, destType.name, false, duration,
                        error = "Permission required (unexpected)")
                }
            }
            
            // Cleanup source file (if we created it)
            if (sourceType == ResourceType.LOCAL) {
                sourceFile.delete()
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", sourceType.name, destType.name, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Matrix copy test failed: ${sourceType.name} → ${destType.name}")
        }
    }
    
    /**
     * Matrix Move Test: Move file from source resource type to destination resource type.
     * Tests all N×N combinations of source → destination.
     */
    private suspend fun testMatrixMove(sourceType: ResourceType, destType: ResourceType) {
        val testName = "Matrix Move: ${sourceType.name} → ${destType.name}"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create unique test file name
            val timestamp = System.currentTimeMillis()
            val sourceFileName = "matrix_move_src_${sourceType.name}_${timestamp}.txt"
            
            // Ensure source file exists
            val sourcePrepare = ensureTestFileExists(sourceType, sourceFileName)
            val sourceFile = sourcePrepare.file
            if (sourceFile == null) {
                recordResult(testName, "Move", sourceType.name, destType.name, false, System.currentTimeMillis() - startTime,
                    error = sourcePrepare.error ?: "Failed to create/access source file on ${sourceType.name}")
                return
            }
            
            // Create destination directory
            val destDir = buildResourcePath(destType, "test_matrix_move_$timestamp")
            if (destDir == null) {
                recordResult(testName, "Move", sourceType.name, destType.name, false, 0,
                    error = "Failed to access destination on ${destType.name}")
                return
            }
            
            log("[$testName] Moving from $sourceFile to $destDir")
            
            // Execute move operation
            val operation = FileOperation.Move(
                sources = listOf(sourceFile),
                destination = destDir,
                overwrite = true
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            when (result) {
                is FileOperationResult.Success -> {
                    recordResult(testName, "Move", sourceType.name, destType.name, true, duration,
                        details = "Moved ${sourceFile.name} to ${destType.name}")
                    
                    // Cleanup: delete moved file
                    val movedFile = File(destDir, sourceFile.name)
                    fileOperationUseCase.execute(FileOperation.Delete(
                        files = listOf(movedFile),
                        softDelete = false
                    ))
                }
                is FileOperationResult.PartialSuccess -> {
                    recordResult(testName, "Move", sourceType.name, destType.name, true, duration,
                        details = "Partial success: ${result.processedCount}/${result.failedCount + result.processedCount}")
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Move", sourceType.name, destType.name, false, duration,
                        error = result.error)
                }
                is FileOperationResult.AuthenticationRequired -> {
                    recordResult(testName, "Move", sourceType.name, destType.name, false, duration,
                        error = "Authentication required for ${result.provider}: ${result.message}")
                }
                is FileOperationResult.PermissionRequired -> {
                    recordResult(testName, "Move", sourceType.name, destType.name, false, duration,
                        error = "Permission required (unexpected)")
                }
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Move", sourceType.name, destType.name, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Matrix move test failed: ${sourceType.name} → ${destType.name}")
        }
    }
    
    /**
     * Matrix Rename Test: Rename file on the specified resource type.
     * Tests rename operation for each protocol/resource type.
     */
    private suspend fun testMatrixRename(resourceType: ResourceType) {
        val testName = "Matrix Rename: ${resourceType.name}"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create unique test file
            val timestamp = System.currentTimeMillis()
            val originalFileName = "matrix_rename_${resourceType.name}_${timestamp}.txt"
            val newFileName = "matrix_renamed_${resourceType.name}_${timestamp}.txt"
            
            // Ensure source file exists
            val sourcePrepare = ensureTestFileExists(resourceType, originalFileName)
            val sourceFile = sourcePrepare.file
            if (sourceFile == null) {
                recordResult(testName, "Rename", resourceType.name, null, false, System.currentTimeMillis() - startTime,
                    error = sourcePrepare.error ?: "Failed to create/access file on ${resourceType.name}")
                return
            }
            
            log("[$testName] Renaming $originalFileName to $newFileName")
            
            // Execute rename operation
            val operation = FileOperation.Rename(
                file = sourceFile,
                newName = newFileName
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            when (result) {
                is FileOperationResult.Success -> {
                    recordResult(testName, "Rename", resourceType.name, null, true, duration,
                        details = "Renamed $originalFileName to $newFileName")
                    
                    // Cleanup: delete renamed file
                    val renamedFile = File(sourceFile.parent, newFileName)
                    fileOperationUseCase.execute(FileOperation.Delete(
                        files = listOf(renamedFile),
                        softDelete = false
                    ))
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Rename", resourceType.name, null, false, duration,
                        error = result.error)
                }
                is FileOperationResult.PartialSuccess -> {
                    recordResult(testName, "Rename", resourceType.name, null, true, duration,
                        details = "Partial success: ${result.processedCount}/${result.failedCount + result.processedCount}")
                }
                is FileOperationResult.AuthenticationRequired -> {
                    recordResult(testName, "Rename", resourceType.name, null, false, duration,
                        error = "Authentication required for ${result.provider}: ${result.message}")
                }
                is FileOperationResult.PermissionRequired -> {
                    recordResult(testName, "Rename", resourceType.name, null, false, duration,
                        error = "Permission required (unexpected)")
                }
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Rename", resourceType.name, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Matrix rename test failed: ${resourceType.name}")
        }
    }
    
    /**
     * Matrix Delete Test: Delete file on the specified resource type.
     * Tests delete operation for each protocol/resource type.
     */
    private suspend fun testMatrixDelete(resourceType: ResourceType) {
        val testName = "Matrix Delete: ${resourceType.name}"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create unique test file
            val timestamp = System.currentTimeMillis()
            val fileName = "matrix_delete_${resourceType.name}_${timestamp}.txt"
            
            // Ensure file exists
            val sourcePrepare = ensureTestFileExists(resourceType, fileName)
            val fileToDelete = sourcePrepare.file
            if (fileToDelete == null) {
                recordResult(testName, "Delete", resourceType.name, null, false, System.currentTimeMillis() - startTime,
                    error = sourcePrepare.error ?: "Failed to create/access file on ${resourceType.name}")
                return
            }
            
            log("[$testName] Deleting $fileName from ${resourceType.name}")
            
            // Execute delete operation
            val operation = FileOperation.Delete(
                files = listOf(fileToDelete),
                softDelete = false
            )
            
            val result = fileOperationUseCase.execute(operation)
            val duration = System.currentTimeMillis() - startTime
            
            when (result) {
                is FileOperationResult.Success -> {
                    recordResult(testName, "Delete", resourceType.name, null, true, duration,
                        details = "Deleted $fileName from ${resourceType.name}")
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Delete", resourceType.name, null, false, duration,
                        error = result.error)
                }
                is FileOperationResult.PartialSuccess -> {
                    recordResult(testName, "Delete", resourceType.name, null, true, duration,
                        details = "Partial success: ${result.processedCount}/${result.failedCount + result.processedCount}")
                }
                is FileOperationResult.AuthenticationRequired -> {
                    recordResult(testName, "Delete", resourceType.name, null, false, duration,
                        error = "Authentication required for ${result.provider}: ${result.message}")
                }
                is FileOperationResult.PermissionRequired -> {
                    recordResult(testName, "Delete", resourceType.name, null, false, duration,
                        error = "Permission required (not handled in test runner)")
                }
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Delete", resourceType.name, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Matrix delete test failed: ${resourceType.name}")
        }
    }

    /**
     * Validate soft-delete eligibility routing for representative path schemes.
     * This protects against regressions where protected Android/media paths
     * accidentally use .trash metadata writes and fail with EPERM.
     */
    private suspend fun testDeletePolicyMatrix() {
        val testName = "Delete Policy Matrix"
        val startTime = System.currentTimeMillis()

        log("[$testName] Starting...")

        try {
            val scenarios = listOf(
                Triple("Local cache path", "${context.cacheDir.absolutePath}/policy_test/file.txt", true),
                Triple("Android media path", "/storage/emulated/0/Android/media/org.telegram.messenger/Telegram/file.jpg", false),
                Triple("Content URI", "content://media/external/images/media/1000110988", false),
                Triple("SMB path", "smb://server/share/folder/file.txt", false),
                Triple("SFTP path", "sftp://server:22/folder/file.txt", false),
                Triple("FTP path", "ftp://server:21/folder/file.txt", false),
                Triple("Cloud path", "cloud://google_drive/folder/file.txt", false)
            )

            val mismatches = scenarios.mapNotNull { (name, path, expected) ->
                val actual = DeletePathPolicy.canUseSoftDelete(path)
                if (actual != expected) "$name expected=$expected actual=$actual path=$path" else null
            }

            val duration = System.currentTimeMillis() - startTime
            if (mismatches.isEmpty()) {
                recordResult(
                    testName,
                    "DeletePolicy",
                    "Policy",
                    null,
                    true,
                    duration,
                    details = "Validated ${scenarios.size} policy scenarios"
                )
            } else {
                recordResult(
                    testName,
                    "DeletePolicy",
                    "Policy",
                    null,
                    false,
                    duration,
                    error = mismatches.joinToString("; ")
                )
            }
        } catch (e: Exception) {
            recordResult(
                testName,
                "DeletePolicy",
                "Policy",
                null,
                false,
                System.currentTimeMillis() - startTime,
                error = e.message
            )
            Timber.e(e, "Delete policy matrix test failed")
        }
    }

    /**
     * Validate delete policy decision for each resource type.
     */
    private suspend fun testDeletePolicyByResourceType(resourceType: ResourceType) {
        val testName = "Delete Policy: ${resourceType.name}"
        val startTime = System.currentTimeMillis()

        try {
            val samplePath = when (resourceType) {
                ResourceType.LOCAL -> "${context.cacheDir.absolutePath}/policy_resource_type/${resourceType.name.lowercase()}_sample.txt"
                ResourceType.SMB -> "smb://integration-server/test-share/policy_sample.txt"
                ResourceType.SFTP -> "sftp://integration-server:22/policy_sample.txt"
                ResourceType.FTP -> "ftp://integration-server:21/policy_sample.txt"
                ResourceType.CLOUD -> "cloud://google_drive/policy_sample.txt"
            }

            val expected = resourceType == ResourceType.LOCAL
            val actual = DeletePathPolicy.canUseSoftDelete(samplePath)
            val duration = System.currentTimeMillis() - startTime

            recordResult(
                testName,
                "DeletePolicy",
                resourceType.name,
                null,
                actual == expected,
                duration,
                error = if (actual == expected) null else "Expected softDelete=$expected, actual=$actual for path=$samplePath",
                details = "Path=$samplePath, expected=$expected, actual=$actual"
            )
        } catch (e: Exception) {
            recordResult(
                testName,
                "DeletePolicy",
                resourceType.name,
                null,
                false,
                System.currentTimeMillis() - startTime,
                error = e.message
            )
            Timber.e(e, "Delete policy resource type test failed: ${resourceType.name}")
        }
    }

    /**
     * Dedicated regression guard for Android/media paths.
     */
    private suspend fun testDeletePolicyAndroidMediaGuard() {
        val testName = "Delete Policy: Android/media Guard"
        val startTime = System.currentTimeMillis()

        try {
            val protectedPath = "/storage/emulated/0/Android/media/org.telegram.messenger/Telegram/Telegram Images/policy_guard.jpg"
            val canUseSoftDelete = DeletePathPolicy.canUseSoftDelete(protectedPath)
            val duration = System.currentTimeMillis() - startTime

            recordResult(
                testName,
                "DeletePolicy",
                "LOCAL",
                null,
                !canUseSoftDelete,
                duration,
                error = if (!canUseSoftDelete) null else "Protected Android/media path incorrectly allowed soft-delete",
                details = "Path=$protectedPath, softDeleteAllowed=$canUseSoftDelete"
            )
        } catch (e: Exception) {
            recordResult(
                testName,
                "DeletePolicy",
                "LOCAL",
                null,
                false,
                System.currentTimeMillis() - startTime,
                error = e.message
            )
            Timber.e(e, "Delete policy Android/media guard test failed")
        }
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Get all testable resource types.
     * Modified to force return all types as per user request to include all tests in the suite,
     * regardless of missing credentials (tests will fail/skip if credentials are missing).
     */
    private suspend fun getAvailableResourceTypes(): List<ResourceType> {
        // Return all resource types to ensure matrix tests are generated for all combinations
        // Credentials will be checked during test execution
        return listOf(
            ResourceType.LOCAL,
            ResourceType.SMB,
            ResourceType.SFTP,
            ResourceType.FTP,
            ResourceType.CLOUD
        )
    }
    
    /**
     * Generate all N×N combinations of source → destination resource pairs.
     */
    private suspend fun generateResourcePairs(): List<Pair<ResourceType, ResourceType>> {
        val availableTypes = getAvailableResourceTypes()
        return buildList {
            for (source in availableTypes) {
                for (dest in availableTypes) {
                    add(source to dest)
                }
            }
        }
    }

    private fun normalizedRemoteFolderPrefix(folder: String?): String {
        val normalized = folder
            ?.trim()
            ?.removePrefix("/")
            ?.removeSuffix("/")
            .orEmpty()
        return if (normalized.isEmpty()) "" else "/$normalized"
    }
    
    /**
     * Build path for a resource type using the correct protocol prefix.
     */
    private suspend fun buildResourcePath(type: ResourceType, subPath: String): File? {
        return when (type) {
            ResourceType.LOCAL -> {
                val dir = File(context.cacheDir, subPath)
                dir.mkdirs()
                dir
            }
            ResourceType.SMB -> {
                val cred = credentialsLoader.getCredential(ResourceType.SMB) ?: return null
                val smbPath = com.sza.fastmediasorter.utils.SmbPathUtils.buildSmbPath(
                    server = cred.server,
                    share = cred.shareName ?: "",
                    path = subPath,
                    port = cred.port ?: 445
                )
                File(smbPath)
            }
            ResourceType.SFTP -> {
                val cred = credentialsLoader.getCredential(ResourceType.SFTP) ?: return null
                val folderPath = normalizedRemoteFolderPrefix(cred.folder)
                val sftpPath = com.sza.fastmediasorter.utils.SftpPathUtils.buildSftpPath(
                    host = cred.server,
                    path = "$folderPath/$subPath",
                    port = cred.port ?: 22
                )
                File(sftpPath)
            }
            ResourceType.FTP -> {
                val cred = credentialsLoader.getCredential(ResourceType.FTP) ?: return null
                val folderPath = normalizedRemoteFolderPrefix(cred.folder)
                File("ftp:///${cred.server}:${cred.port}$folderPath/$subPath")
            }
            ResourceType.CLOUD -> {
                // Resolve CLOUD base path from configured DB resources (same source as provider tests).
                // This avoids hard dependency on a generic CLOUD credential entry.
                val cloudResource = resourceRepository
                    .getAllResourcesSync()
                    .firstOrNull { it.type == ResourceType.CLOUD && it.path.startsWith("cloud://") }
                    ?: return null

                val basePath = cloudResource.path.trimEnd('/')
                val normalizedSubPath = subPath.trim().trimStart('/')
                if (normalizedSubPath.isBlank()) {
                    File(basePath)
                } else {
                    File("$basePath/$normalizedSubPath")
                }
            }
        }
    }
    
    private data class TestFilePreparationResult(
        val file: File?,
        val error: String? = null
    )

    /**
     * Get or create a test file on the specified resource type.
     * For network resources, uploads a file first if it doesn't exist.
     */
    private suspend fun ensureTestFileExists(type: ResourceType, fileName: String): TestFilePreparationResult {
        return when (type) {
            ResourceType.LOCAL -> {
                try {
                    val file = File(context.cacheDir, "test_matrix/$fileName")
                    file.parentFile?.mkdirs()
                    if (!file.exists()) {
                        file.writeText("Test content for matrix test: ${System.currentTimeMillis()}")
                    }
                    TestFilePreparationResult(file = file)
                } catch (e: Exception) {
                    TestFilePreparationResult(
                        file = null,
                        error = "Local source setup failed: ${e.message}"
                    )
                }
            }
            else -> {
                // For network resources, we need to upload a local file first
                val destDir = buildResourcePath(type, "test_matrix")
                    ?: return TestFilePreparationResult(
                        file = null,
                        error = "Failed to resolve setup destination on ${type.name}"
                    )

                Timber.d("Matrix source setup: type=${type.name}, destination=$destDir, file=$fileName")

                // Use the same fileName for local temp file so after upload the remote file has correct name
                val localTestFile = File(context.cacheDir, "test_matrix_setup/$fileName")
                localTestFile.parentFile?.mkdirs()
                localTestFile.writeText("Test content for matrix test: ${System.currentTimeMillis()}")
                
                // Upload to network location
                val copyOp = FileOperation.Copy(
                    sources = listOf(localTestFile),
                    destination = destDir,
                    overwrite = true
                )
                val result = fileOperationUseCase.execute(copyOp)
                localTestFile.delete()
                
                when (result) {
                    is FileOperationResult.Success,
                    is FileOperationResult.PartialSuccess -> {
                        TestFilePreparationResult(file = File(destDir, fileName))
                    }
                    is FileOperationResult.Failure -> {
                        val error = "Source setup failed on ${type.name}: ${result.error}"
                        Timber.e("ensureTestFileExists: $error")
                        TestFilePreparationResult(file = null, error = error)
                    }
                    is FileOperationResult.AuthenticationRequired -> {
                        val error = "Source setup auth failed on ${type.name}: ${result.message}"
                        Timber.w("ensureTestFileExists: $error")
                        TestFilePreparationResult(file = null, error = error)
                    }
                    is FileOperationResult.PermissionRequired -> {
                        val error = "Source setup permission required on ${type.name}"
                        Timber.w("ensureTestFileExists: $error")
                        TestFilePreparationResult(file = null, error = error)
                    }
                }
            }
        }
    }
    
    private fun createTestFile(name: String, content: String): File {
        val file = File(context.cacheDir, name)
        file.writeText(content)
        return file
    }
    
    private fun recordResult(
        testName: String,
        operation: String,
        sourceType: String,
        destType: String?,
        success: Boolean,
        duration: Long,
        error: String? = null,
        details: String? = null
    ) {
        val result = TestResult(testName, operation, sourceType, destType, success, duration, error, details)
        testResults.add(result)
        
        val status = if (success) "✅ PASS" else "❌ FAIL"
        log("[$testName] $status (${duration}ms)")
        if (details != null) log("  Details: $details")
        if (error != null) log("  Error: $error")
        log("")
    }

    private fun recordSkipped(
        testName: String,
        operation: String,
        sourceType: String,
        destType: String? = null,
        reason: String
    ) {
        recordResult(
            testName = testName,
            operation = operation,
            sourceType = sourceType,
            destType = destType,
            success = true,
            duration = 0,
            details = "SKIPPED: $reason"
        )
    }
    
    private fun generateFinalReport() {
        log("=".repeat(80))
        log("TEST SUITE COMPLETE")
        log("=".repeat(80))
        log("")
        
        val passed = testResults.count { it.success }
        val failed = testResults.count { !it.success }
        val total = testResults.size
        
        // Store failed test names for rerun
        lastFailedTests.clear()
        lastFailedTests.addAll(testResults.filter { !it.success }.map { it.testName })
        
        // Save to SharedPreferences for persistence
        prefs.edit().putStringSet("failed_tests", lastFailedTests).apply()
        
        log("Summary:")
        log("  Total Tests: $total")
        if (total > 0) {
            log("  Passed: $passed (${(passed * 100 / total)}%)")
            log("  Failed: $failed (${(failed * 100 / total)}%)")
        } else {
            log("  No tests were executed")
        }
        log("")
        
        // Cloud provider-specific summary
        val cloudTests = testResults.filter { 
            it.sourceType in listOf("ONEDRIVE", "DROPBOX", "GOOGLE_DRIVE") ||
            it.destType in listOf("ONEDRIVE", "DROPBOX", "GOOGLE_DRIVE")
        }
        
        if (cloudTests.isNotEmpty()) {
            log("Cloud Provider Summary:")
            listOf("ONEDRIVE", "DROPBOX", "GOOGLE_DRIVE").forEach { provider ->
                val providerTests = cloudTests.filter { 
                    it.sourceType == provider || it.destType == provider ||
                    it.testName.contains(provider.replace("_", " "), ignoreCase = true)
                }
                if (providerTests.isNotEmpty()) {
                    val providerPassed = providerTests.count { it.success }
                    val providerFailed = providerTests.count { !it.success }
                    val providerTotal = providerTests.size
                    val status = if (providerFailed == 0) "✅" else "⚠️"
                    log("  $status $provider: $providerPassed/$providerTotal passed")
                }
            }
            log("")
        }
        
        if (failed > 0) {
            log("Failed Tests:")
            testResults.filter { !it.success }.forEach { result ->
                log("  ❌ ${result.testName}: ${result.error ?: "Unknown error"}")
            }
            log("")
        }
        
        log("Detailed Results:")
        
        // Group by cloud providers first
        if (cloudTests.isNotEmpty()) {
            log("")
            log("  === CLOUD PROVIDERS ===")
            listOf("ONEDRIVE", "DROPBOX", "GOOGLE_DRIVE").forEach { provider ->
                val providerTests = cloudTests.filter { 
                    it.sourceType == provider || it.destType == provider ||
                    it.testName.contains(provider.replace("_", " "), ignoreCase = true)
                }
                if (providerTests.isNotEmpty()) {
                    log("  --- $provider ---")
                    providerTests.forEach { result ->
                        val status = if (result.success) "✅" else "❌"
                        val dest = result.destType?.let { " → $it" } ?: ""
                        log("    $status ${result.operation}: ${result.sourceType}$dest (${result.duration}ms)")
                    }
                }
            }
        }
        
        // Show other tests
        val otherTests = testResults.filter { 
            it !in cloudTests
        }
        if (otherTests.isNotEmpty()) {
            log("")
            log("  === OTHER TESTS ===")
            otherTests.forEach { result ->
                val status = if (result.success) "✅" else "❌"
                val dest = result.destType?.let { " → $it" } ?: ""
                log("  $status ${result.operation}: ${result.sourceType}$dest (${result.duration}ms)")
            }
        }
        
        log("")
        log("End Time: ${getCurrentTimestamp()}")
        log("=".repeat(80))
    }
    
    private fun log(message: String) {
        logBuilder.appendLine(message)
        Timber.tag("IntegrationTest").d(message)
    }
    
    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
    
    // ========== IMAGE EDIT TEST IMPLEMENTATIONS ==========
    
    /**
     * Test image rotation by specified angle.
     * Creates a temporary test image, rotates it, and verifies success.
     */
    private suspend fun testImageRotate(angle: Float) {
        val testName = "Image Rotate ${angle.toInt()}°"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create a test image (simple 100x100 red square)
            val testImageFile = createTestImage("rotate_test.jpg")
            if (testImageFile == null) {
                recordResult(testName, "Rotate", "LOCAL", null, false, 
                    System.currentTimeMillis() - startTime, "Failed to create test image")
                return
            }
            
            log("[$testName] Created test image: ${testImageFile.absolutePath}")
            log("[$testName] Rotating by $angle degrees...")
            
            val result = rotateImageUseCase.execute(testImageFile.absolutePath, angle)
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                recordResult(testName, "Rotate", "LOCAL", null, true, duration, 
                    details = "Rotated image by $angle degrees")
            } else {
                recordResult(testName, "Rotate", "LOCAL", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
            // Cleanup
            testImageFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Rotate", "LOCAL", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    /**
     * Test image flip in specified direction.
     */
    private suspend fun testImageFlip(direction: FlipImageUseCase.FlipDirection) {
        val testName = "Image Flip ${direction.name}"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val testImageFile = createTestImage("flip_test.jpg")
            if (testImageFile == null) {
                recordResult(testName, "Flip", "LOCAL", null, false,
                    System.currentTimeMillis() - startTime, "Failed to create test image")
                return
            }
            
            log("[$testName] Flipping ${direction.name}...")
            
            val result = flipImageUseCase.execute(testImageFile.absolutePath, direction)
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                recordResult(testName, "Flip", "LOCAL", null, true, duration,
                    details = "Flipped image ${direction.name}")
            } else {
                recordResult(testName, "Flip", "LOCAL", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
            // Cleanup
            testImageFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Flip", "LOCAL", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    /**
     * Test applying image filter.
     */
    private suspend fun testImageFilter(filterType: ApplyImageFilterUseCase.FilterType) {
        val testName = "Image Filter ${filterType.name}"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val testImageFile = createTestImage("filter_test.jpg")
            if (testImageFile == null) {
                recordResult(testName, "Filter", "LOCAL", null, false,
                    System.currentTimeMillis() - startTime, "Failed to create test image")
                return
            }
            
            log("[$testName] Applying ${filterType.name} filter...")
            
            val result = applyImageFilterUseCase.execute(testImageFile.absolutePath, filterType)
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                recordResult(testName, "Filter", "LOCAL", null, true, duration,
                    details = "Applied ${filterType.name} filter")
            } else {
                recordResult(testName, "Filter", "LOCAL", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
            // Cleanup
            testImageFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Filter", "LOCAL", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    /**
     * Test image adjustments (brightness, contrast, saturation).
     */
    private suspend fun testImageAdjust(adjustments: AdjustImageUseCase.Adjustments) {
        val adjustmentType = when {
            adjustments.brightness != 0f -> "Brightness"
            adjustments.contrast != 1f -> "Contrast"
            adjustments.saturation != 1f -> "Saturation"
            else -> "Combined"
        }
        val testName = "Image Adjust $adjustmentType"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val testImageFile = createTestImage("adjust_test.jpg")
            if (testImageFile == null) {
                recordResult(testName, "Adjust", "LOCAL", null, false,
                    System.currentTimeMillis() - startTime, "Failed to create test image")
                return
            }
            
            log("[$testName] Adjusting $adjustmentType...")
            
            val result = adjustImageUseCase.execute(testImageFile.absolutePath, adjustments)
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                recordResult(testName, "Adjust", "LOCAL", null, true, duration,
                    details = "Applied $adjustmentType adjustment")
            } else {
                recordResult(testName, "Adjust", "LOCAL", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
            // Cleanup
            testImageFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Adjust", "LOCAL", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    // ========== GIF TEST IMPLEMENTATIONS ==========
    
    /**
     * Test GIF frame extraction.
     */
    private suspend fun testGifExtractFrames() {
        val testName = "GIF Extract Frames"
        val startTime = System.currentTimeMillis()
        
        try {
            val testGifFile = findTestGifFile()
            if (testGifFile == null) {
                recordResult(testName, "Extract", "LOCAL", null, false,
                    System.currentTimeMillis() - startTime, "No test GIF file found in test_media/")
                return
            }
            
            log("[$testName] Extracting frames from: ${testGifFile.absolutePath}")
            
            val result = extractGifFramesUseCase.execute(testGifFile.absolutePath)
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                val frameCount = result.getOrNull() ?: 0
                recordResult(testName, "Extract", "LOCAL", null, true, duration,
                    details = "Extracted $frameCount frames")
            } else {
                recordResult(testName, "Extract", "LOCAL", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Extract", "LOCAL", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    /**
     * Test saving GIF first frame.
     */
    private suspend fun testGifSaveFirstFrame() {
        val testName = "GIF Save First Frame"
        val startTime = System.currentTimeMillis()
        
        try {
            val testGifFile = findTestGifFile()
            if (testGifFile == null) {
                recordResult(testName, "SaveFrame", "LOCAL", null, false,
                    System.currentTimeMillis() - startTime, "No test GIF file found in test_media/")
                return
            }
            
            log("[$testName] Saving first frame from: ${testGifFile.absolutePath}")
            
            val result = saveGifFirstFrameUseCase.execute(testGifFile.absolutePath)
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                val outputPath = result.getOrNull()
                recordResult(testName, "SaveFrame", "LOCAL", null, true, duration,
                    details = "Saved first frame to: $outputPath")
            } else {
                recordResult(testName, "SaveFrame", "LOCAL", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
        } catch (e: Exception) {
            recordResult(testName, "SaveFrame", "LOCAL", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    /**
     * Test changing GIF speed.
     */
    private suspend fun testGifChangeSpeed(speedMultiplier: Float) {
        val testName = "GIF Speed ${speedMultiplier}x"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val testGifFile = findTestGifFile()
            if (testGifFile == null) {
                recordResult(testName, "ChangeSpeed", "LOCAL", null, false,
                    System.currentTimeMillis() - startTime, "No test GIF file found in test_media/")
                return
            }
            
            // Work on a copy to not modify the original test file
            val workCopy = File(context.cacheDir, "speed_test_${System.currentTimeMillis()}.gif")
            testGifFile.copyTo(workCopy, overwrite = true)
            
            log("[$testName] Changing speed to ${speedMultiplier}x: ${workCopy.absolutePath}")
            
            // Use saveToDownloads = true to avoid modifying the copy (it will save to Downloads)
            val result = changeGifSpeedUseCase.execute(workCopy.absolutePath, speedMultiplier, saveToDownloads = true)
            
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                val outputPath = result.getOrNull()
                recordResult(testName, "ChangeSpeed", "LOCAL", null, true, duration,
                    details = "Changed speed to ${speedMultiplier}x, output: $outputPath")
            } else {
                recordResult(testName, "ChangeSpeed", "LOCAL", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
            // Cleanup
            workCopy.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "ChangeSpeed", "LOCAL", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
        }
    }
    
    // ========== SETTINGS TEST IMPLEMENTATIONS (Module 1) ==========
    
    /**
     * Test settings export functionality.
     * Exports settings to XML file and verifies the file exists and contains data.
     */
    private suspend fun testSettingsExport() {
        val testName = "Settings Export"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val result = exportSettingsUseCase.invoke()
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                val exportPath = result.getOrNull() ?: ""
                
                // Verify file exists - handle both content:// URIs and file paths
                val (exists, size) = if (exportPath.startsWith("content://")) {
                    // Android 10+: Use ContentResolver to verify
                    try {
                        val uri = android.net.Uri.parse(exportPath)
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            Pair(bytes.isNotEmpty(), bytes.size.toLong())
                        } ?: Pair(false, 0L)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to verify export via ContentResolver")
                        Pair(false, 0L)
                    }
                } else {
                    // Legacy: Direct file access
                    val exportFile = File(exportPath)
                    Pair(exportFile.exists() && exportFile.length() > 0, exportFile.length())
                }
                
                if (exists && size > 0) {
                    recordResult(testName, "Export", "SETTINGS", null, true, duration,
                        details = "Exported $size bytes")
                } else {
                    recordResult(testName, "Export", "SETTINGS", null, false, duration,
                        error = "Export file is empty or doesn't exist")
                }
            } else {
                recordResult(testName, "Export", "SETTINGS", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown export error")
            }
        } catch (e: Exception) {
            recordResult(testName, "Export", "SETTINGS", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Settings export test failed")
        }
    }
    
    /**
     * Test settings import functionality.
     * Assumes a previous export file exists, imports it and verifies success.
     */
    private suspend fun testSettingsImport() {
        val testName = "Settings Import"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // First ensure export file exists by exporting
            val exportResult = exportSettingsUseCase.invoke()
            if (exportResult.isFailure) {
                recordResult(testName, "Import", "SETTINGS", null, false, 0,
                    error = "Failed to create export file for import test")
                return
            }
            
            val exportUri = exportResult.getOrNull()
            log("[$testName] Export file created, now testing import...")
            
            // Now test import - pass the export URI directly
            val result = importSettingsUseCase.invoke(exportUri)
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                recordResult(testName, "Import", "SETTINGS", null, true, duration,
                    details = "Successfully imported settings from XML")
            } else {
                recordResult(testName, "Import", "SETTINGS", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown import error")
            }
        } catch (e: Exception) {
            recordResult(testName, "Import", "SETTINGS", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Settings import test failed")
        }
    }
    
    /**
     * Test settings round-trip: Export → Import → Verify consistency.
     * This ensures that exported settings can be successfully re-imported.
     */
    private suspend fun testSettingsRoundTrip() {
        val testName = "Settings Round-trip"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Step 1: Export current settings
            log("[$testName] Step 1: Exporting settings...")
            val exportResult = exportSettingsUseCase.invoke()
            if (exportResult.isFailure) {
                recordResult(testName, "RoundTrip", "SETTINGS", null, false, 0,
                    error = "Step 1 failed: ${exportResult.exceptionOrNull()?.message}")
                return
            }
            
            val exportPath = exportResult.getOrNull()
            log("[$testName] Exported to: $exportPath")
            
            // Step 2: Import settings back - pass the export URI directly
            log("[$testName] Step 2: Importing settings...")
            val importResult = importSettingsUseCase.invoke(exportPath)
            if (importResult.isFailure) {
                recordResult(testName, "RoundTrip", "SETTINGS", null, false,
                    System.currentTimeMillis() - startTime,
                    error = "Step 2 failed: ${importResult.exceptionOrNull()?.message}")
                return
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            // Success: Both export and import succeeded
            recordResult(testName, "RoundTrip", "SETTINGS", null, true, duration,
                details = "Export → Import cycle completed successfully")
                
        } catch (e: Exception) {
            recordResult(testName, "RoundTrip", "SETTINGS", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Settings round-trip test failed")
        }
    }
    
    // ========== FAVORITES TEST IMPLEMENTATIONS (Module 2) ==========
    
    /**
     * Test adding a file to favorites.
     */
    private suspend fun testFavoritesAdd() {
        val testName = "Favorites Add"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create a test MediaFile
            val testFile = createTestFile("favorite_test_${System.currentTimeMillis()}.txt", "Test favorite content")
            val mediaFile = com.sza.fastmediasorter.domain.model.MediaFile(
                name = testFile.name,
                path = testFile.absolutePath,
                type = com.sza.fastmediasorter.domain.model.MediaType.TEXT,
                size = testFile.length(),
                createdDate = System.currentTimeMillis()
            )
            
            // Add to favorites
            favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L)
            
            // Verify it's a favorite
            val isFavorite = favoritesUseCase.isFavoriteSync(mediaFile.path)
            val duration = System.currentTimeMillis() - startTime
            
            if (isFavorite) {
                recordResult(testName, "Add", "FAVORITES", null, true, duration,
                    details = "Added ${mediaFile.name} to favorites")
            } else {
                recordResult(testName, "Add", "FAVORITES", null, false, duration,
                    error = "File not marked as favorite after toggle")
            }
            
            // Cleanup: remove from favorites and delete file
            favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L)
            testFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Add", "FAVORITES", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Favorites add test failed")
        }
    }
    
    /**
     * Test removing a file from favorites.
     */
    private suspend fun testFavoritesRemove() {
        val testName = "Favorites Remove"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create and add a test file to favorites
            val testFile = createTestFile("favorite_remove_${System.currentTimeMillis()}.txt", "Test content")
            val mediaFile = com.sza.fastmediasorter.domain.model.MediaFile(
                name = testFile.name,
                path = testFile.absolutePath,
                type = com.sza.fastmediasorter.domain.model.MediaType.TEXT,
                size = testFile.length(),
                createdDate = System.currentTimeMillis()
            )
            
            // Add to favorites first
            favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L)
            
            // Remove from favorites
            favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L)
            
            // Verify it's not a favorite anymore
            val isFavorite = favoritesUseCase.isFavoriteSync(mediaFile.path)
            val duration = System.currentTimeMillis() - startTime
            
            if (!isFavorite) {
                recordResult(testName, "Remove", "FAVORITES", null, true, duration,
                    details = "Removed ${mediaFile.name} from favorites")
            } else {
                recordResult(testName, "Remove", "FAVORITES", null, false, duration,
                    error = "File still marked as favorite after removal")
            }
            
            // Cleanup
            testFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Remove", "FAVORITES", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Favorites remove test failed")
        }
    }
    
    /**
     * Test toggling favorite status multiple times.
     */
    private suspend fun testFavoritesToggle() {
        val testName = "Favorites Toggle"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val testFile = createTestFile("favorite_toggle_${System.currentTimeMillis()}.txt", "Toggle test")
            val mediaFile = com.sza.fastmediasorter.domain.model.MediaFile(
                name = testFile.name,
                path = testFile.absolutePath,
                type = com.sza.fastmediasorter.domain.model.MediaType.TEXT,
                size = testFile.length(),
                createdDate = System.currentTimeMillis()
            )
            
            // Toggle on
            favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L)
            val firstToggle = favoritesUseCase.isFavoriteSync(mediaFile.path)
            
            // Toggle off
            favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L)
            val secondToggle = favoritesUseCase.isFavoriteSync(mediaFile.path)
            
            // Toggle on again
            favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L)
            val thirdToggle = favoritesUseCase.isFavoriteSync(mediaFile.path)
            
            val duration = System.currentTimeMillis() - startTime
            
            val success = firstToggle && !secondToggle && thirdToggle
            
            if (success) {
                recordResult(testName, "Toggle", "FAVORITES", null, true, duration,
                    details = "Toggle sequence: ON → OFF → ON successful")
            } else {
                recordResult(testName, "Toggle", "FAVORITES", null, false, duration,
                    error = "Toggle sequence failed: $firstToggle → $secondToggle → $thirdToggle")
            }
            
            // Cleanup
            favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L) // Remove if still favorited
            testFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Toggle", "FAVORITES", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Favorites toggle test failed")
        }
    }
    
    /**
     * Test retrieving list of all favorites.
     */
    private suspend fun testFavoritesList() {
        val testName = "Favorites List"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Add multiple test files to favorites
            val testFiles = mutableListOf<File>()
            val mediaFiles = mutableListOf<com.sza.fastmediasorter.domain.model.MediaFile>()
            
            for (i in 1..3) {
                val testFile = createTestFile("fav_list_$i.txt", "Content $i")
                testFiles.add(testFile)
                
                val mediaFile = com.sza.fastmediasorter.domain.model.MediaFile(
                    path = testFile.absolutePath,
                    name = testFile.name,
                    size = testFile.length(),
                    type = com.sza.fastmediasorter.domain.model.MediaType.TEXT,
                    createdDate = System.currentTimeMillis()
                )
                mediaFiles.add(mediaFile)
                favoritesUseCase.toggleFavorite(mediaFile, resourceId = 1L)
            }
            
            // Get all favorites
            val favorites = favoritesUseCase.getAllFavorites().first()
            val duration = System.currentTimeMillis() - startTime
            
            // Verify our test files are in the list
            val testPaths = mediaFiles.map { it.path }.toSet()
            val foundCount = favorites.count { it.uri in testPaths }
            
            if (foundCount == 3) {
                recordResult(testName, "List", "FAVORITES", null, true, duration,
                    details = "Successfully retrieved ${favorites.size} favorites (found all 3 test items)")
            } else {
                recordResult(testName, "List", "FAVORITES", null, false, duration,
                    error = "Expected 3 test favorites in list, found $foundCount")
            }
            
            // Cleanup: remove all added favorites and delete files
            mediaFiles.forEach { favoritesUseCase.toggleFavorite(it, resourceId = 1L) }
            testFiles.forEach { it.delete() }
            
        } catch (e: Exception) {
            recordResult(testName, "List", "FAVORITES", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Favorites list test failed")
        }
    }
    
    // ========== METADATA TEST IMPLEMENTATIONS (Module 3) ==========
    
    /**
     * Test EXIF metadata extraction from images.
     */
    private suspend fun testExtractExifMetadata() {
        val testName = "EXIF Metadata Extract"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create a test image with EXIF data
            val testImageFile = createTestImage("exif_test_${System.currentTimeMillis()}.jpg")
            if (testImageFile == null) {
                recordResult(testName, "EXIF", "METADATA", null, false, 0,
                    error = "Failed to create test image")
                return
            }
            
            log("[$testName] Extracting EXIF metadata from: ${testImageFile.name}")
            
            val result = kotlin.runCatching {
                extractExifMetadataUseCase.extractFromFile(testImageFile.absolutePath)
            }
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                val exifData = result.getOrNull()
                val hasData = exifData != null
                
                recordResult(testName, "EXIF", "METADATA", null, hasData, duration,
                    details = if (hasData) "Extracted EXIF data successfully" else "No EXIF data found")
            } else {
                recordResult(testName, "EXIF", "METADATA", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
            // Cleanup
            testImageFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "EXIF", "METADATA", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "EXIF metadata extraction test failed")
        }
    }
    
    /**
     * Test video metadata extraction.
     * Note: This test may be skipped if no test video file is available.
     */
    private suspend fun testExtractVideoMetadata() {
        val testName = "Video Metadata Extract"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Try to find a test video file
            val testVideoFile = findTestVideoFile()
            if (testVideoFile == null) {
                // No video file available — skip gracefully (not a failure, just no test data)
                recordResult(testName, "Video", "METADATA", null, true, 0,
                    details = "SKIPPED: no test video file available on device")
                return
            }
            
            log("[$testName] Extracting video metadata from: ${testVideoFile.name}")
            
            val result = kotlin.runCatching {
                extractVideoMetadataUseCase.extractFromFile(testVideoFile.absolutePath)
            }
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                val videoData = result.getOrNull()
                val hasData = videoData != null && (videoData.duration != null || videoData.width != null)
                
                recordResult(testName, "Video", "METADATA", null, hasData, duration,
                    details = if (hasData) {
                        "Duration: ${videoData?.duration}ms, Size: ${videoData?.width}x${videoData?.height}"
                    } else "No video metadata found")
            } else {
                recordResult(testName, "Video", "METADATA", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Unknown error")
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Video", "METADATA", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Video metadata extraction test failed")
        }
    }
    
    // ========== CLOUD PROVIDER TEST IMPLEMENTATIONS (Module 6) ==========
    
    /**
     * Test copy operation for a specific cloud provider.
     * Uses cloud resource from database.
     */
    private suspend fun testCloudProviderCopy(provider: CloudProvider) {
        val testName = "${provider.name} Copy"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Find cloud resource from database
            val cloudResource = findCloudResource(provider)
                ?: run {
                    ensureCloudResourceForProvider(provider)
                    findCloudResource(provider)
                }
            if (cloudResource == null) {
                recordSkipped(testName, "Copy", provider.name, null,
                    "${provider.name} test resource not found in database")
                log("[$testName] Skipped - no resource configured")
                return
            }
            
            // Create a test file
            val testFile = createTestFile("cloud_test_${provider.name.lowercase()}_${System.currentTimeMillis()}.txt", 
                "Test content for ${provider.name}")
            
            log("[$testName] Created test file: ${testFile.name}")
            
            // Define source (local) and destination (cloud) paths
            val sourcePath = testFile.absolutePath
            val destinationPath = "${cloudResource.path.trimEnd('/')}/${testFile.name}"
            
            log("[$testName] Copying from $sourcePath to $destinationPath")
            
            // Execute copy operation
            val result = fileOperationUseCase.execute(
                operation = FileOperation.Copy(
                    sources = listOf(testFile),
                    destination = File(destinationPath),
                    overwrite = true
                )
            )
            
            val duration = System.currentTimeMillis() - startTime
            
            when (result) {
                is FileOperationResult.Success -> {
                    recordResult(testName, "Copy", provider.name, destinationPath, true, duration,
                        details = "Copied ${testFile.name} to ${cloudResource.name}")
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Copy", provider.name, destinationPath, false, duration,
                        error = result.error)
                }
                else -> {
                    recordResult(testName, "Copy", provider.name, destinationPath, false, duration,
                        error = "Unexpected result type")
                }
            }
            
            // Cleanup: delete local test file
            testFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Copy", provider.name, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "${provider.name} copy test failed")
        }
    }
    
    /**
     * Test rename operation for a specific cloud provider.
     * Skips test if provider credentials are not configured.
     */
    private suspend fun testCloudProviderRename(provider: CloudProvider) {
        val testName = "${provider.name} Rename"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val cloudResource = findCloudResource(provider)
                ?: run {
                    ensureCloudResourceForProvider(provider)
                    findCloudResource(provider)
                }
            if (cloudResource == null) {
                recordSkipped(testName, "Rename", provider.name, null,
                    "${provider.name} test resource not found in database")
                log("[$testName] Skipped - no resource configured")
                return
            }
            
            // First, we need to create a file on the cloud provider
            val testFile = createTestFile("cloud_rename_test_${System.currentTimeMillis()}.txt", 
                "Test content for ${provider.name} rename")
            
            val originalName = testFile.name
            val newName = "renamed_${originalName}"
            
            val cloudBasePath = cloudResource.path.trimEnd('/')
            val cloudPath = "$cloudBasePath/$originalName"
            val newCloudPath = "$cloudBasePath/$newName"
            
            log("[$testName] Step 1: Uploading $originalName to ${provider.name}...")
            
            // Step 1: Upload file to cloud
            val uploadResult = fileOperationUseCase.execute(
                operation = FileOperation.Copy(
                    sources = listOf(testFile),
                    destination = File(cloudPath),
                    overwrite = true
                )
            )
            
            if (uploadResult !is FileOperationResult.Success) {
                val duration = System.currentTimeMillis() - startTime
                recordResult(testName, "Rename", provider.name, null, false, duration,
                    error = "Upload failed: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                testFile.delete()
                return
            }
            
            log("[$testName] Step 2: Renaming $originalName to $newName...")
            
            // Step 2: Rename file on cloud
            val renameResult = fileOperationUseCase.execute(
                operation = FileOperation.Rename(
                    file = File(cloudPath),
                    newName = newName
                )
            )
            
            val duration = System.currentTimeMillis() - startTime
            
            when (renameResult) {
                is FileOperationResult.Success -> {
                    recordResult(testName, "Rename", provider.name, newCloudPath, true, duration,
                        details = "Renamed $originalName → $newName on ${provider.name}")
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Rename", provider.name, cloudPath, false, duration,
                        error = renameResult.error)
                }
                else -> {
                    recordResult(testName, "Rename", provider.name, cloudPath, false, duration,
                        error = "Unexpected result type")
                }
            }
            
            // Cleanup: delete local test file
            testFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Rename", provider.name, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "${provider.name} rename test failed")
        }
    }
    
    /**
     * Test upload (local → cloud) for a specific cloud provider.
     */
    private suspend fun testCloudProviderUpload(provider: CloudProvider) {
        val testName = "${provider.name} Upload"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val cloudResource = findCloudResource(provider)
                ?: run {
                    ensureCloudResourceForProvider(provider)
                    findCloudResource(provider)
                }
            if (cloudResource == null) {
                recordSkipped(testName, "Upload", provider.name, null,
                    "${provider.name} test resource not found in database")
                log("[$testName] Skipped - no resource configured")
                return
            }
            
            // Create a test file
            val testFile = createTestFile("cloud_upload_${provider.name.lowercase()}_${System.currentTimeMillis()}.txt", 
                "Upload test for ${provider.name}")
            
            val cloudBasePath = cloudResource.path.trimEnd('/')
            val cloudPath = "$cloudBasePath/${testFile.name}"
            
            log("[$testName] Uploading ${testFile.name} to $cloudPath")
            
            val result = fileOperationUseCase.execute(
                operation = FileOperation.Copy(
                    sources = listOf(testFile),
                    destination = File(cloudPath).parentFile ?: File(cloudPath),
                    overwrite = true
                )
            )
            
            val duration = System.currentTimeMillis() - startTime
            
            when (result) {
                is FileOperationResult.Success -> {
                    recordResult(testName, "Upload", provider.name, cloudPath, true, duration,
                        details = "Uploaded ${testFile.name} to ${provider.name}")
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Upload", provider.name, cloudPath, false, duration,
                        error = result.error)
                }
                else -> {
                    recordResult(testName, "Upload", provider.name, cloudPath, false, duration,
                        error = "Unexpected result type")
                }
            }
            
            testFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Upload", provider.name, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "${provider.name} upload test failed")
        }
    }
    
    /**
     * Test download (cloud → local) for a specific cloud provider.
     */
    private suspend fun testCloudProviderDownload(provider: CloudProvider) {
        val testName = "${provider.name} Download"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val cloudResource = findCloudResource(provider)
                ?: run {
                    ensureCloudResourceForProvider(provider)
                    findCloudResource(provider)
                }
            if (cloudResource == null) {
                recordSkipped(testName, "Download", provider.name, null,
                    "${provider.name} test resource not found in database")
                log("[$testName] Skipped - no resource configured")
                return
            }
            
            // First upload a file
            val testFile = createTestFile("cloud_download_test_${System.currentTimeMillis()}.txt", 
                "Download test for ${provider.name}")
            
            val cloudBasePath = cloudResource.path.trimEnd('/')
            val cloudPath = "$cloudBasePath/${testFile.name}"
            
            log("[$testName] Step 1: Uploading test file...")
            
            val uploadResult = fileOperationUseCase.execute(
                operation = FileOperation.Copy(
                    sources = listOf(testFile),
                    destination = File(cloudPath).parentFile ?: File(cloudPath),
                    overwrite = true
                )
            )
            
            if (uploadResult !is FileOperationResult.Success) {
                val duration = System.currentTimeMillis() - startTime
                recordResult(testName, "Download", provider.name, null, false, duration,
                    error = "Upload failed: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                testFile.delete()
                return
            }
            
            log("[$testName] Step 2: Downloading file from cloud...")
            
            // Now download it
            val localDestDir = File(context.cacheDir, "test_cloud_download_${System.currentTimeMillis()}")
            localDestDir.mkdirs()
            
            val downloadResult = fileOperationUseCase.execute(
                operation = FileOperation.Copy(
                    sources = listOf(File(cloudPath)),
                    destination = localDestDir,
                    overwrite = true
                )
            )
            
            val duration = System.currentTimeMillis() - startTime
            
            when (downloadResult) {
                is FileOperationResult.Success -> {
                    val downloadedFile = File(localDestDir, testFile.name)
                    val success = downloadedFile.exists()
                    
                    recordResult(testName, "Download", provider.name, cloudPath, success, duration,
                        details = "Downloaded ${testFile.name} from ${provider.name}, size=${downloadedFile.length()} bytes")
                    
                    downloadedFile.delete()
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Download", provider.name, cloudPath, false, duration,
                        error = downloadResult.error)
                }
                else -> {
                    recordResult(testName, "Download", provider.name, cloudPath, false, duration,
                        error = "Unexpected result type")
                }
            }
            
            testFile.delete()
            localDestDir.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Download", provider.name, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "${provider.name} download test failed")
        }
    }
    
    /**
     * Test delete operation for a specific cloud provider.
     */
    private suspend fun testCloudProviderDelete(provider: CloudProvider) {
        val testName = "${provider.name} Delete"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val cloudResource = findCloudResource(provider)
                ?: run {
                    ensureCloudResourceForProvider(provider)
                    findCloudResource(provider)
                }
            if (cloudResource == null) {
                recordSkipped(testName, "Delete", provider.name, null,
                    "${provider.name} test resource not found in database")
                log("[$testName] Skipped - no resource configured")
                return
            }
            
            // First upload a file to delete
            val testFile = createTestFile("cloud_delete_test_${System.currentTimeMillis()}.txt", 
                "Delete test for ${provider.name}")
            
            val cloudBasePath = cloudResource.path.trimEnd('/')
            val cloudPath = "$cloudBasePath/${testFile.name}"
            
            log("[$testName] Step 1: Uploading test file...")
            
            val uploadResult = fileOperationUseCase.execute(
                operation = FileOperation.Copy(
                    sources = listOf(testFile),
                    destination = File(cloudPath).parentFile ?: File(cloudPath),
                    overwrite = true
                )
            )
            
            if (uploadResult !is FileOperationResult.Success) {
                val duration = System.currentTimeMillis() - startTime
                recordResult(testName, "Delete", provider.name, null, false, duration,
                    error = "Upload failed: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                testFile.delete()
                return
            }
            
            log("[$testName] Step 2: Deleting file from cloud...")
            
            val deleteResult = fileOperationUseCase.execute(
                operation = FileOperation.Delete(
                    files = listOf(File(cloudPath)),
                    softDelete = false
                )
            )
            
            val duration = System.currentTimeMillis() - startTime
            
            when (deleteResult) {
                is FileOperationResult.Success -> {
                    recordResult(testName, "Delete", provider.name, cloudPath, true, duration,
                        details = "Deleted ${testFile.name} from ${provider.name}")
                }
                is FileOperationResult.Failure -> {
                    recordResult(testName, "Delete", provider.name, cloudPath, false, duration,
                        error = deleteResult.error)
                }
                else -> {
                    recordResult(testName, "Delete", provider.name, cloudPath, false, duration,
                        error = "Unexpected result type")
                }
            }
            
            testFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Delete", provider.name, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "${provider.name} delete test failed")
        }
    }

    private suspend fun testCloudMoveSameProviderPathBased() {
        val testName = "Cloud Move Same Provider Path"
        val startTime = System.currentTimeMillis()

        log("[$testName] Starting...")

        try {
            val provider = when {
                findCloudResource(CloudProvider.ONEDRIVE) != null -> CloudProvider.ONEDRIVE
                findCloudResource(CloudProvider.DROPBOX) != null -> CloudProvider.DROPBOX
                findCloudResource(CloudProvider.GOOGLE_DRIVE) != null -> CloudProvider.GOOGLE_DRIVE
                else -> null
            }

            if (provider == null) {
                recordResult(
                    testName,
                    "Move",
                    "CLOUD",
                    "CLOUD",
                    true,
                    0,
                    details = "SKIPPED: no cloud test resources configured"
                )
                return
            }

            val resource = findCloudResource(provider)
            if (resource == null) {
                recordResult(
                    testName,
                    "Move",
                    "CLOUD",
                    "CLOUD",
                    true,
                    0,
                    details = "SKIPPED: cloud resource for $provider not found"
                )
                return
            }

            val timestamp = System.currentTimeMillis()
            val localSource = createTestFile("cloud_move_same_$timestamp.txt", "same-provider move test")
            val cloudBasePath = resource.path.trimEnd('/')
            val sourceCloudPath = "$cloudBasePath/${localSource.name}"
            val targetFolderPath = "$cloudBasePath/move_same_provider_$timestamp"

            val uploadResult = fileOperationUseCase.execute(
                FileOperation.Copy(
                    sources = listOf(localSource),
                    destination = File(cloudBasePath),
                    overwrite = true
                )
            )

            if (uploadResult !is FileOperationResult.Success) {
                recordResult(
                    testName,
                    "Move",
                    provider.name,
                    provider.name,
                    false,
                    System.currentTimeMillis() - startTime,
                    error = "Setup upload failed: ${(uploadResult as? FileOperationResult.Failure)?.error}"
                )
                localSource.delete()
                return
            }

            val moveResult = fileOperationUseCase.execute(
                FileOperation.Move(
                    sources = listOf(File(sourceCloudPath)),
                    destination = File(targetFolderPath),
                    overwrite = true
                )
            )

            val success = moveResult is FileOperationResult.Success ||
                (moveResult is FileOperationResult.PartialSuccess && moveResult.processedCount > 0)

            recordResult(
                testName,
                "Move",
                provider.name,
                provider.name,
                success,
                System.currentTimeMillis() - startTime,
                error = if (success) null else (moveResult as? FileOperationResult.Failure)?.error ?: "Move failed with ${moveResult::class.simpleName}",
                details = if (success) "Moved from $sourceCloudPath to $targetFolderPath" else null
            )

            localSource.delete()
        } catch (e: Exception) {
            recordResult(
                testName,
                "Move",
                "CLOUD",
                "CLOUD",
                false,
                System.currentTimeMillis() - startTime,
                error = e.message
            )
        }
    }

    private suspend fun testCloudMoveCrossProvider() {
        val testName = "Cloud Move Cross Provider"
        val startTime = System.currentTimeMillis()

        log("[$testName] Starting...")

        try {
            val sourceProvider = when {
                findCloudResource(CloudProvider.ONEDRIVE) != null -> CloudProvider.ONEDRIVE
                findCloudResource(CloudProvider.DROPBOX) != null -> CloudProvider.DROPBOX
                else -> null
            }

            val destProvider = when (sourceProvider) {
                CloudProvider.ONEDRIVE -> if (findCloudResource(CloudProvider.DROPBOX) != null) CloudProvider.DROPBOX else null
                CloudProvider.DROPBOX -> if (findCloudResource(CloudProvider.ONEDRIVE) != null) CloudProvider.ONEDRIVE else null
                else -> null
            }

            if (sourceProvider == null || destProvider == null) {
                recordResult(
                    testName,
                    "Move",
                    "CLOUD",
                    "CLOUD",
                    true,
                    0,
                    details = "SKIPPED: requires at least two cloud providers (ONEDRIVE + DROPBOX)"
                )
                return
            }

            val sourceResource = findCloudResource(sourceProvider)
            val destResource = findCloudResource(destProvider)
            if (sourceResource == null || destResource == null) {
                recordResult(
                    testName,
                    "Move",
                    sourceProvider.name,
                    destProvider.name,
                    true,
                    0,
                    details = "SKIPPED: source or destination cloud resource missing"
                )
                return
            }

            val timestamp = System.currentTimeMillis()
            val localSource = createTestFile("cloud_move_cross_$timestamp.txt", "cross-provider move test")

            val sourceBase = sourceResource.path.trimEnd('/')
            val sourceCloudPath = "$sourceBase/${localSource.name}"
            val destFolderPath = "${destResource.path.trimEnd('/')}/move_cross_provider_$timestamp"

            val uploadResult = fileOperationUseCase.execute(
                FileOperation.Copy(
                    sources = listOf(localSource),
                    destination = File(sourceBase),
                    overwrite = true
                )
            )

            if (uploadResult !is FileOperationResult.Success) {
                recordResult(
                    testName,
                    "Move",
                    sourceProvider.name,
                    destProvider.name,
                    false,
                    System.currentTimeMillis() - startTime,
                    error = "Setup upload failed: ${(uploadResult as? FileOperationResult.Failure)?.error}"
                )
                localSource.delete()
                return
            }

            val moveResult = fileOperationUseCase.execute(
                FileOperation.Move(
                    sources = listOf(File(sourceCloudPath)),
                    destination = File(destFolderPath),
                    overwrite = true
                )
            )

            val success = moveResult is FileOperationResult.Success ||
                (moveResult is FileOperationResult.PartialSuccess && moveResult.processedCount > 0)

            recordResult(
                testName,
                "Move",
                sourceProvider.name,
                destProvider.name,
                success,
                System.currentTimeMillis() - startTime,
                error = if (success) null else (moveResult as? FileOperationResult.Failure)?.error ?: "Cross-provider move failed with ${moveResult::class.simpleName}",
                details = if (success) "Moved from $sourceProvider to $destProvider via runtime fallback path" else null
            )

            localSource.delete()
        } catch (e: Exception) {
            recordResult(
                testName,
                "Move",
                "CLOUD",
                "CLOUD",
                false,
                System.currentTimeMillis() - startTime,
                error = e.message
            )
        }
    }
    
    // ========== AUDIO TEST IMPLEMENTATIONS (Module 7) ==========
    
    /**
     * Test lyrics search functionality.
     * Uses a well-known song title to ensure search works.
     */
    private suspend fun testSearchLyrics() {
        val testName = "Lyrics Search"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Create a fake MediaFile with a well-known song name
            val testMediaFile = com.sza.fastmediasorter.domain.model.MediaFile(
                name = "Bohemian Rhapsody - Queen.mp3",
                path = "/storage/emulated/0/Music/test.mp3",
                type = com.sza.fastmediasorter.domain.model.MediaType.AUDIO,
                size = 1000L,
                createdDate = System.currentTimeMillis()
            )
            
            log("[$testName] Searching lyrics for: ${testMediaFile.name}")
            
            val result = searchLyricsUseCase.execute(testMediaFile)
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                val lyrics = result.getOrNull()
                val hasLyrics = !lyrics.isNullOrBlank() && lyrics.length > 50
                
                recordResult(testName, "Search", "AUDIO", null, hasLyrics, duration,
                    details = if (hasLyrics) "Found lyrics (${lyrics?.length ?: 0} chars)" 
                             else "No lyrics found")
            } else {
                recordResult(testName, "Search", "AUDIO", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Lyrics search failed")
            }
        } catch (e: Exception) {
            recordResult(testName, "Search", "AUDIO", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Lyrics search test failed")
        }
    }
    
    /**
     * Test cover art search functionality.
     * Uses a well-known song/album to ensure search works.
     */
    private suspend fun testSearchCoverArt() {
        val testName = "Cover Art Search"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Search for a well-known song
            val searchQuery = "Bohemian Rhapsody Queen"
            log("[$testName] Searching cover art for: $searchQuery")
            
            val result = searchAudioCoverUseCase.invoke(searchQuery)
            val duration = System.currentTimeMillis() - startTime
            
            val coverUrl = result?.coverArtUrl
            val hasCover = !coverUrl.isNullOrEmpty()
            
            val details = if (hasCover) {
                val metadata = "${result?.artistName ?: "?"} - ${result?.trackName ?: "?"} (${result?.albumName ?: "?"}, ${result?.releaseYear ?: "?"})"
                "Found: $metadata, URL: ${coverUrl?.take(50)}..."
            } else {
                "No cover art found (may be disabled in settings)"
            }
            
            recordResult(testName, "Search", "AUDIO", null, hasCover, duration, details = details)
            
        } catch (e: Exception) {
            recordResult(testName, "Search", "AUDIO", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Cover art search test failed")
        }
    }
    
    // ========== NETWORK UTILITY TEST IMPLEMENTATIONS (Module 8) ==========
    
    /**
     * Test network discovery functionality.
     * Scans local network for available hosts with open ports.
     */
    private suspend fun testNetworkDiscovery() {
        val testName = "Network Discovery"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val hosts = mutableListOf<NetworkHost>()
            
            // Collect up to 5 hosts or timeout after 30 seconds
            kotlinx.coroutines.withTimeoutOrNull(30_000) {
                discoverNetworkResourcesUseCase.execute().collect { host ->
                    hosts.add(host)
                    log("[$testName] Found: ${host.ip} (${host.hostname}) - ports: ${host.openPorts}")
                    if (hosts.size >= 5) return@collect
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            recordResult(testName, "Discovery", "NETWORK", null, true, duration,
                details = "Found ${hosts.size} hosts on local network")
            
        } catch (e: Exception) {
            recordResult(testName, "Discovery", "NETWORK", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Network discovery test failed")
        }
    }
    
    /**
     * Test network speed test functionality for a specific protocol.
     */
    private suspend fun testNetworkSpeedTest(resourceType: com.sza.fastmediasorter.domain.model.ResourceType) {
        val testName = "${resourceType.name} Speed Test"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            // Note: NetworkSpeedTestUseCase requires credentials to be registered in database
            // with credentialsId. For integration testing, we skip this test since it requires
            // database setup. This test should be run manually or in full app environment.
            
            recordSkipped(testName, "SpeedTest", resourceType.name, null,
                "requires database credentials registration")
            
            log("[$testName] Skipped - requires database credentials registration")
            
        } catch (e: Exception) {
            recordResult(testName, "SpeedTest", resourceType.name, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "${resourceType.name} speed test failed")
        }
    }
    
    /**
     * Test network file download functionality.
     */
    private suspend fun testNetworkDownload(protocol: String) {
        val testName = "$protocol Download"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val resourceType = when (protocol) {
                "SMB" -> com.sza.fastmediasorter.domain.model.ResourceType.SMB
                "SFTP" -> com.sza.fastmediasorter.domain.model.ResourceType.SFTP
                "FTP" -> com.sza.fastmediasorter.domain.model.ResourceType.FTP
                else -> null
            }
            val credential = resourceType?.let { credentialsLoader.getCredential(it) }
            if (credential == null) {
                recordSkipped(testName, "Download", protocol, null,
                    "$protocol credentials not configured")
                return
            }
            
            // First, upload a test file to download
            val sourceFile = createTestFile("download_test_${System.currentTimeMillis()}.txt", 
                "Test content for $protocol download")
            
            val remoteFolderPath = when (protocol) {
                "SMB" -> "smb://${credential.server}/${credential.shareName ?: "test_media"}"
                "SFTP" -> com.sza.fastmediasorter.utils.SftpPathUtils.buildSftpPath(
                    host = credential.server,
                    path = credential.folder ?: "/",
                    port = credential.port ?: 22
                )
                "FTP" -> {
                    // Use credential folder if set; fall back to "test_integration" to avoid
                    // writing to FTP root (which many servers disallow or misconfigure)
                    val effectiveFolder = credential.folder?.takeIf { it.isNotBlank() } ?: "test_integration"
                    val folderPath = normalizedRemoteFolderPrefix(effectiveFolder)
                    "ftp://${credential.server}:${credential.port ?: 21}$folderPath"
                }
                else -> ""
            }
            
            val remotePath = "$remoteFolderPath/${sourceFile.name}"
            
            // Upload the file first
            val uploadResult = fileOperationUseCase.execute(
                operation = FileOperation.Copy(
                    sources = listOf(sourceFile),
                    destination = File(remoteFolderPath),
                    overwrite = true
                )
            )
            
            if (uploadResult !is FileOperationResult.Success) {
                val duration = System.currentTimeMillis() - startTime
                recordResult(testName, "Download", protocol, null, false, duration,
                    error = "Upload failed: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                sourceFile.delete()
                return
            }
            
            log("[$testName] File uploaded, now downloading...")
            
            // Now download it
            val targetFile = File(context.cacheDir, "downloaded_${sourceFile.name}")
            // var progress = 0
            
            val downloadResult = downloadNetworkFileUseCase.execute(
                remotePath = remotePath,
                targetFile = targetFile,
                progressCallback = null
            )
            
            val duration = System.currentTimeMillis() - startTime
            
            if (downloadResult && targetFile.exists()) {
                val content = targetFile.readText()
                val contentMatch = content.contains("Test content for $protocol download")
                
                recordResult(testName, "Download", protocol, null, contentMatch, duration,
                    details = "Downloaded ${targetFile.length()} bytes, content verified: $contentMatch")
            } else {
                recordResult(testName, "Download", protocol, null, false, duration,
                    error = "Download failed or file not created")
            }
            
            // Cleanup
            sourceFile.delete()
            targetFile.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "Download", protocol, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "$protocol download test failed")
        }
    }
    
    /**
     * Test network image edit functionality (download → edit → upload).
     */
    private suspend fun testNetworkImageEdit(protocol: String) {
        val testName = "$protocol Image Edit"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val resourceType = when (protocol) {
                "SMB" -> com.sza.fastmediasorter.domain.model.ResourceType.SMB
                "SFTP" -> com.sza.fastmediasorter.domain.model.ResourceType.SFTP
                "FTP" -> com.sza.fastmediasorter.domain.model.ResourceType.FTP
                else -> null
            }
            val credential = resourceType?.let { credentialsLoader.getCredential(it) }
            if (credential == null) {
                recordResult(testName, "ImageEdit", protocol, null, false, 0,
                    error = "$protocol credentials not configured - test skipped")
                return
            }
            
            // Create a test image
            val testImage = createTestImage("network_edit_test_${System.currentTimeMillis()}.jpg")
            if (testImage == null) {
                recordResult(testName, "ImageEdit", protocol, null, false, 0,
                    error = "Failed to create test image")
                return
            }
            
            // Upload to network location
            val remotePath = when (protocol) {
                "SMB" -> "smb://${credential.server}/${credential.shareName ?: "share"}/${testImage.name}"
                "SFTP" -> "sftp://${credential.server}/${credential.folder?.removePrefix("/") ?: ""}/${testImage.name}"
                "FTP" -> "ftp://${credential.server}/${credential.folder?.removePrefix("/") ?: ""}/${testImage.name}"
                else -> ""
            }
            
            val uploadResult = fileOperationUseCase.execute(
                operation = FileOperation.Copy(
                    sources = listOf(testImage),
                    destination = File(remotePath).parentFile ?: File(remotePath),
                    overwrite = true
                )
            )
            
            if (uploadResult !is FileOperationResult.Success) {
                val duration = System.currentTimeMillis() - startTime
                recordResult(testName, "ImageEdit", protocol, null, false, duration,
                    error = "Upload failed: ${(uploadResult as? FileOperationResult.Failure)?.error}")
                testImage.delete()
                return
            }
            
            log("[$testName] Image uploaded, now applying rotation...")
            
            // Perform network image edit (rotate 90 degrees)
            val editResult = networkImageEditUseCase.execute(
                networkPath = remotePath,
                operation = NetworkImageEditUseCase.EditOperation.Rotate(90f)
            )
            
            val duration = System.currentTimeMillis() - startTime
            
            if (editResult.isSuccess) {
                recordResult(testName, "ImageEdit", protocol, null, true, duration,
                    details = "Successfully rotated image on $protocol")
            } else {
                recordResult(testName, "ImageEdit", protocol, null, false, duration,
                    error = editResult.exceptionOrNull()?.message ?: "Image edit failed")
            }
            
            // Cleanup
            testImage.delete()
            
        } catch (e: Exception) {
            recordResult(testName, "ImageEdit", protocol, null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "$protocol image edit test failed")
        }
    }
    
    // ========== CACHE TEST IMPLEMENTATIONS (Module 9) ==========
    
    /**
     * Test optimal cache size calculation.
     */
    private suspend fun testCalculateOptimalCacheSize() {
        val testName = "Optimal Cache Size"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val optimalSizeMB = calculateOptimalCacheSizeUseCase.invoke()
            val totalStorageGB = calculateOptimalCacheSizeUseCase.getTotalInternalStorageGB()
            
            val duration = System.currentTimeMillis() - startTime
            
            // Validate result is reasonable (between 1GB and 8GB)
            val isValid = optimalSizeMB in 1024..8192
            
            recordResult(testName, "Calculate", "CACHE", null, isValid, duration,
                details = "Optimal cache: ${optimalSizeMB}MB for ${totalStorageGB}GB storage")
            
        } catch (e: Exception) {
            recordResult(testName, "Calculate", "CACHE", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Cache size calculation test failed")
        }
    }
    
    // ========== SCANNING TEST IMPLEMENTATIONS (Module 10) ==========
    
    /**
     * Test local folder scanning functionality.
     */
    private suspend fun testScanLocalFolders() {
        val testName = "Local Folder Scan"
        val startTime = System.currentTimeMillis()
        
        log("[$testName] Starting...")
        
        try {
            val result = scanLocalFoldersUseCase.invoke()
            val duration = System.currentTimeMillis() - startTime
            
            if (result.isSuccess) {
                val folders = result.getOrNull() ?: emptyList()
                recordResult(testName, "Scan", "LOCAL", null, true, duration,
                    details = "Found ${folders.size} folders on device")
            } else {
                recordResult(testName, "Scan", "LOCAL", null, false, duration,
                    error = result.exceptionOrNull()?.message ?: "Scan failed")
            }
            
        } catch (e: Exception) {
            recordResult(testName, "Scan", "LOCAL", null, false,
                System.currentTimeMillis() - startTime, error = e.message)
            Timber.e(e, "Local folder scan test failed")
        }
    }
    
    // ========== HELPER METHODS FOR IMAGE/GIF TESTS ==========
    
    /**
     * Create a simple test image (100x100 red square) for testing image operations.
     */
    private fun createTestImage(fileName: String): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            
            // Create a simple 100x100 bitmap with red color
            val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.RED)
            
            // Save as JPEG
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            bitmap.recycle()
            
            if (file.exists() && file.length() > 0) {
                file
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create test image: $fileName")
            null
        }
    }
    
    /**
     * Find a test GIF file in test_media folder or create a simple one.
     * Returns null if no GIF file is available.
     */
    private fun findTestGifFile(): File? {
        // First try app's external files directory (where test_credentials.json is)
        val appTestMediaDir = File(context.getExternalFilesDir(null), "test_media")
        if (appTestMediaDir.exists()) {
            val gifFiles = appTestMediaDir.listFiles { file ->
                file.extension.equals("gif", ignoreCase = true)
            }
            if (!gifFiles.isNullOrEmpty()) {
                Timber.d("Found GIF in app external dir: ${gifFiles.first().absolutePath}")
                return gifFiles.first()
            }
        }
        
        // Try project test folder
        val projectRoot = File("/storage/emulated/0/Download/FastMediaSorter_test")
        val testMediaDir = File(projectRoot, "test_media")
        
        if (testMediaDir.exists()) {
            val gifFiles = testMediaDir.listFiles { file ->
                file.extension.equals("gif", ignoreCase = true)
            }
            if (!gifFiles.isNullOrEmpty()) {
                Timber.d("Found GIF in project test dir: ${gifFiles.first().absolutePath}")
                return gifFiles.first()
            }
        }
        
        // Try cache directory
        val cacheGifDir = File(context.cacheDir, "test_media")
        if (cacheGifDir.exists()) {
            val gifFiles = cacheGifDir.listFiles { file ->
                file.extension.equals("gif", ignoreCase = true)
            }
            if (!gifFiles.isNullOrEmpty()) {
                Timber.d("Found GIF in cache dir: ${gifFiles.first().absolutePath}")
                return gifFiles.first()
            }
        }
        
        // No GIF found, create a simple test GIF programmatically
        Timber.d("No test GIF file found, creating one programmatically")
        return createTestGif("test_animated.gif")
    }
    
    /**
     * Create a simple animated GIF for testing.
     * Uses AnimatedGifEncoder with proper LZW compression.
     */
    private fun createTestGif(fileName: String): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            
            // Create 3 frames with different colors (small size for speed)
            val width = 50
            val height = 50
            val colors = listOf(
                android.graphics.Color.RED,
                android.graphics.Color.GREEN,
                android.graphics.Color.BLUE
            )
            
            val frames = colors.map { color ->
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(color)
                bitmap
            }
            
            // Write GIF using AnimatedGifEncoder with proper LZW compression
            java.io.FileOutputStream(file).use { out ->
                val encoder = com.sza.fastmediasorter.util.gif.AnimatedGifEncoder()
                encoder.start(out)
                encoder.setDelay(500) // 500ms delay between frames
                encoder.setRepeat(0) // Loop forever
                
                for (frame in frames) {
                    encoder.addFrame(frame)
                }
                
                encoder.finish()
            }
            
            // Recycle bitmaps
            frames.forEach { it.recycle() }
            
            if (file.exists() && file.length() > 0) {
                Timber.d("Created test GIF: ${file.absolutePath} (${file.length()} bytes)")
                file
            } else {
                Timber.w("Failed to create test GIF - file is empty or doesn't exist")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create test GIF: $fileName")
            null
        }
    }
    
    /**
     * Find a test video file in test_media folder.
     * Returns null if no video file is available.
     */
    private fun findTestVideoFile(): File? {
        // First try to find existing video in test_media
        val projectRoot = File("/storage/emulated/0/Download/FastMediaSorter_test")
        val testMediaDir = File(projectRoot, "test_media")
        
        if (testMediaDir.exists()) {
            val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
            val videoFiles = testMediaDir.listFiles { file ->
                videoExtensions.any { ext -> file.extension.equals(ext, ignoreCase = true) }
            }
            if (!videoFiles.isNullOrEmpty()) {
                return videoFiles.first()
            }
        }
        
        // Try cache directory
        val cacheMediaDir = File(context.cacheDir, "test_media")
        if (cacheMediaDir.exists()) {
            val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
            val videoFiles = cacheMediaDir.listFiles { file ->
                videoExtensions.any { ext -> file.extension.equals(ext, ignoreCase = true) }
            }
            if (!videoFiles.isNullOrEmpty()) {
                return videoFiles.first()
            }
        }
        
        Timber.w("No test video file found for video metadata tests")
        return null
    }
    


    private data class Test(
        val name: String,
        val execute: suspend () -> Unit
    )
}
