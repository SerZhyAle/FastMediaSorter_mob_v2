package com.sza.fastmediasorter.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.usecase.IntegrationTestRunner
import com.sza.fastmediasorter.domain.usecase.IntegrationTestRunner.TestGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for integration test dialog.
 */
@HiltViewModel
class IntegrationTestViewModel @Inject constructor(
    private val testRunner: IntegrationTestRunner
) : ViewModel() {
    
    private val _testProgress = MutableStateFlow<IntegrationTestRunner.TestProgress?>(null)
    val testProgress: StateFlow<IntegrationTestRunner.TestProgress?> = _testProgress.asStateFlow()
    
    private val _testLog = MutableStateFlow("")
    val testLog: StateFlow<String> = _testLog.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    /**
     * Run all tests.
     */
    fun runTests() {
        runTestGroup(TestGroup.ALL)
    }
    
    /**
     * Run tests for a specific group.
     */
    fun runTestGroup(group: TestGroup) {
        if (_isRunning.value) {
            Timber.w("Tests already running")
            return
        }
        
        viewModelScope.launch {
            try {
                _isRunning.value = true
                _testLog.value = "Starting ${group.displayName} tests...\n"
                
                testRunner.runTestGroup(group).collect { progress ->
                    _testProgress.value = progress
                    _testLog.value = testRunner.getTestLog()
                }
                
                _testLog.value = testRunner.getTestLog()
                
            } catch (e: Exception) {
                Timber.e(e, "Error running tests")
                _testLog.value += "\n\nERROR: ${e.message}\n"
            } finally {
                _isRunning.value = false
            }
        }
    }
    
    fun copyLogToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Integration Test Log", testRunner.getTestLog())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    fun saveLogToFile(context: Context) {
        viewModelScope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "integration_test_log_$timestamp.txt"
                val file = File(context.getExternalFilesDir(null), fileName)
                
                file.writeText(testRunner.getTestLog())
                
                Toast.makeText(context, "Log saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                Timber.d("Log saved to: ${file.absolutePath}")
                
            } catch (e: Exception) {
                Timber.e(e, "Error saving log")
                Toast.makeText(context, "Error saving log: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun importLogFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val logContent = clip.getItemAt(0).text.toString()
            parseLogAndSetFailedTests(logContent, context)
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseLogAndSetFailedTests(logContent: String, context: Context) {
        viewModelScope.launch {
            try {
                // Regex to match: [Test Name] ❌ FAIL
                val regex = Regex("\\[(.*?)\\] ❌ FAIL")
                val failedTests = regex.findAll(logContent)
                    .map { it.groupValues[1] }
                    .distinct() // Avoid duplicates if log contains multiple runs
                    .toList()
                
                if (failedTests.isNotEmpty()) {
                    testRunner.setFailedTests(failedTests)
                    _testLog.value += "\nImported ${failedTests.size} failed tests from clipboard.\n"
                    failedTests.forEach { _testLog.value += "  - $it\n" }
                    // Update log view to scroll to bottom happens via flow collection in UI
                    Toast.makeText(context, "Imported ${failedTests.size} failed tests", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No failed tests found in clipboard content", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing log")
                Toast.makeText(context, "Error parsing log: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
