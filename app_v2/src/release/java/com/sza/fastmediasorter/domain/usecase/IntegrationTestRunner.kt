package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * No-op stub for release builds. Real implementation in src/debug/.
 */
class IntegrationTestRunner @Inject constructor(
    @ApplicationContext private val context: Context
) {
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

    fun setFailedTests(testNames: List<String>) {}
    fun runTestGroup(group: TestGroup): Flow<TestProgress> = flow { }
    fun runAllTests(): Flow<TestProgress> = runTestGroup(TestGroup.ALL)
    fun getTestLog(): String = ""
    fun getTestResults(): List<TestResult> = emptyList()
}
