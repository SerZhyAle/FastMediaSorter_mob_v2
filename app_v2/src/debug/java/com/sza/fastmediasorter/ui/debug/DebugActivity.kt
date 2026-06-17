package com.sza.fastmediasorter.ui.debug

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.DebugFeatureFlags
import com.sza.fastmediasorter.core.debug.DebugToolsBootstrap
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import com.sza.fastmediasorter.core.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class DebugActivity : AppCompatActivity() {

    private lateinit var tvLogContent: TextView
    private lateinit var spinnerLevel: Spinner
    private lateinit var etSearch: EditText
    private lateinit var switchLeakCanary: SwitchMaterial

    private var fullLogText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)

        tvLogContent = findViewById(R.id.tvLogContent)
        spinnerLevel = findViewById(R.id.spinnerLogLevel)
        etSearch = findViewById(R.id.etLogSearch)
        switchLeakCanary = findViewById(R.id.switchLeakCanary)

        setupLevelSpinner()
        setupActions()
        setupFeatureToggles()
        loadLatestLog()
    }

    private fun setupFeatureToggles() {
        val enabled = DebugFeatureFlags.isLeakCanaryEnabled(this)
        switchLeakCanary.isChecked = enabled

        switchLeakCanary.setOnCheckedChangeListener { _, isChecked ->
            DebugFeatureFlags.setLeakCanaryEnabled(this, isChecked)
            DebugToolsBootstrap.applyLeakCanarySetting(this)
            val messageRes = if (isChecked) R.string.debug_leakcanary_enabled else R.string.debug_leakcanary_disabled
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLevelSpinner() {
        val levels = listOf("ALL", "E", "W", "I", "D")
        spinnerLevel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, levels)
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btnRefreshLogs).setOnClickListener {
            loadLatestLog()
        }

        findViewById<MaterialButton>(R.id.btnApplyFilter).setOnClickListener {
            applyFilters()
        }

        findViewById<MaterialButton>(R.id.btnClearLogs).setOnClickListener {
            clearLogs()
        }

        findViewById<MaterialButton>(R.id.btnShareLogs).setOnClickListener {
            shareLatestLog()
        }

        findViewById<MaterialButton>(R.id.btnOpenNetworkInspector).setOnClickListener {
            openChucker()
        }

        findViewById<MaterialButton>(R.id.btnClearGlideCache).setOnClickListener {
            clearGlideCache()
        }

        findViewById<MaterialButton>(R.id.btnResetPreferences).setOnClickListener {
            resetPreferences()
        }
    }

    private fun loadLatestLog() {
        lifecycleScope.launch {
            fullLogText = withContext(Dispatchers.IO) {
                val latest = getLatestLogFile() ?: return@withContext getString(R.string.debug_logs_empty)
                latest.readText(Charsets.UTF_8)
            }
            applyFilters()
        }
    }

    private fun applyFilters() {
        val selectedLevel = spinnerLevel.selectedItem?.toString().orEmpty()
        val query = etSearch.text?.toString().orEmpty().trim()

        val filtered = fullLogText
            .lineSequence()
            .filter { line -> selectedLevel == "ALL" || line.contains(" $selectedLevel/") }
            .filter { line -> query.isBlank() || line.contains(query, ignoreCase = true) }
            .joinToString("\n")

        tvLogContent.text = filtered.ifBlank { getString(R.string.debug_logs_empty) }
    }

    private fun clearLogs() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dir = getLogDirectory()
            dir?.listFiles { file -> file.isFile && file.name.endsWith(".log") }?.forEach { it.delete() }
            withContext(Dispatchers.Main) {
                fullLogText = ""
                tvLogContent.text = getString(R.string.debug_logs_empty)
                Toast.makeText(this@DebugActivity, R.string.debug_logs_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareLatestLog() {
        val latest = getLatestLogFile()
        if (latest == null || !latest.exists()) {
            Toast.makeText(this, R.string.debug_logs_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", latest)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.debug_share_logs)))
    }

    private fun openChucker() {
        val opened = runCatching {
            val clazz = Class.forName("com.chuckerteam.chucker.api.Chucker")
            val method = clazz.getMethod("getLaunchIntent", android.content.Context::class.java)
            val intent = method.invoke(null, this) as? Intent
            if (intent != null) {
                startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!opened) {
            Toast.makeText(this, R.string.debug_network_inspector_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearGlideCache() {
        Glide.get(this).clearMemory()
        lifecycleScope.launch(Dispatchers.IO) {
            Glide.get(this@DebugActivity).clearDiskCache()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@DebugActivity, R.string.debug_glide_cache_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetPreferences() {
        Timber.d("S0486: debug reset preferences (DataStore + theme + language)")
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val dataStoreFile = applicationContext.preferencesDataStoreFile("settings")
                if (dataStoreFile.exists()) {
                    dataStoreFile.delete()
                }
            }

            runCatching {
                getSharedPreferences("${packageName}_preferences", MODE_PRIVATE).edit().clear().apply()
            }

            runCatching {
                ColorThemePrefs.reset(applicationContext)
            }

            runCatching {
                LocaleHelper.resetLanguage(applicationContext)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@DebugActivity, R.string.debug_preferences_reset, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getLogDirectory(): File? {
        return getExternalFilesDir(null)?.resolve("logs")
    }

    private fun getLatestLogFile(): File? {
        return getLogDirectory()
            ?.listFiles { file -> file.isFile && file.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
    }
}