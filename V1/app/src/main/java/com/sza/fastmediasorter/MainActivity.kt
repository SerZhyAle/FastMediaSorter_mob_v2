package com.sza.fastmediasorter

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter.data.ConnectionConfig
import com.sza.fastmediasorter.databinding.ActivityMainBinding
import com.sza.fastmediasorter.ui.ConnectionViewModel
import com.sza.fastmediasorter.ui.MainPagerAdapter
import com.sza.fastmediasorter.ui.base.LocaleActivity
import com.sza.fastmediasorter.ui.network.NetworkFragment
import com.sza.fastmediasorter.ui.slideshow.SlideshowActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter.utils.ErrorDialogHelper
import com.sza.fastmediasorter.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * MainActivity is the primary entry point of the FastMediaSorter application.
 * It provides a tabbed interface for accessing local and network media folders,
 * with controls for starting slideshows and sorting operations.
 *
 * Key features:
 * - Tabbed navigation between local folders and network connections
 * - Slideshow interval configuration
 * - Permission handling for media access
 * - First launch welcome flow integration
 * - Session auto-resume functionality
 *
 * The activity manages the main application state and coordinates between
 * different fragments and activities for media browsing and organization.
 */
class MainActivity : LocaleActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager
    private val viewModel: ConnectionViewModel by viewModels()
    private var currentConfigId: Long? = null
    private var currentConfig: ConnectionConfig? = null

    private val requestPermissionLauncher =
        registerForActivityResult(
            androidx
                .activity
                .result
                .contract
                .ActivityResultContracts
                .RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                // Permission granted - show restart dialog
                showRestartDialog()
            } else {
                // Permission denied - open main screen with network tab and show message
                openMainWithNetworkTab()
            }
        }

    /**
     * Called when the activity is first created.
     * Initializes the UI components, sets up ViewPager with tabs, configures click listeners,
     * handles first launch welcome flow, and attempts to auto-resume previous sessions.
     *
     * @param savedInstanceState Bundle containing the activity's previously saved state, or null if none exists
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        preferenceManager = PreferenceManager(this)

        // Initialize interval field with saved value
        binding.intervalInput.setText(preferenceManager.getInterval().toString())

        // Auto-add local folders to sort destinations (first launch or after scan)
        lifecycleScope.launch {
            // Only add local folders to sort destinations if media permission granted and first scan completed
            if (com
                    .sza
                    .fastmediasorter
                    .network
                    .LocalStorageClient
                    .hasMediaPermission(this@MainActivity)
            ) {
                // Ensure local folders are in database first
                viewModel.ensureStandardLocalFoldersInDatabase()
                preferenceManager.setFirstLocalScanCompleted()

                // Auto-add Camera and Download as destinations only on first launch
                if (!preferenceManager.isWelcomeShown()) {
                    viewModel.autoAddLocalFoldersAsSortDestinations()
                }
            }
            // Fix SMB write permissions for existing connections
            viewModel.fixSmbWritePermissions()
        }

        setupViewPager()
        setupClickListeners()

// Check for first launch - show Welcome activity
        if (!preferenceManager.isWelcomeShown()) {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("isFirstLaunch", true)
            startActivity(intent)
        } else {
            // Check if we just returned from welcome and need to request permission
            val justReturnedFromWelcome = preferenceManager.isReturnedFromWelcome()
            if (justReturnedFromWelcome &&
                !com
                    .sza
                    .fastmediasorter
                    .network
                    .LocalStorageClient
                    .hasMediaPermission(this)
            ) {
                requestMediaPermission()
            } else if (justReturnedFromWelcome &&
                com
                    .sza
                    .fastmediasorter
                    .network
                    .LocalStorageClient
                    .hasMediaPermission(this)
            ) {
                // Returned from welcome with permission already granted - open settings
                preferenceManager.setReturnedFromWelcome(false) // Clear flag
                openSettingsForFirstLaunch()
            }
        }

        tryAutoResumeSession()
    }

    /**
     * Sets up the ViewPager with tab navigation for local folders and network connections.
     * Configures the adapter, tab layout mediator, and fragment callbacks for handling
     * folder selection and double-click events.
     */
    private fun setupViewPager() {
        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text =
                when (position) {
                    0 -> getString(R.string.local_folders)
                    1 -> getString(R.string.network)
                    else -> ""
                }
        }.attach()
        binding.viewPager.offscreenPageLimit = 2

        // Switch to Network tab if no media permission
        if (!com
                .sza
                .fastmediasorter
                .network
                .LocalStorageClient
                .hasMediaPermission(this)
        ) {
            binding.viewPager.setCurrentItem(1, false)
        }

        binding.viewPager.post {
            val networkFragment = supportFragmentManager.findFragmentByTag("f1") as? NetworkFragment
            networkFragment?.let { setupNetworkFragmentCallbacks(it) }
            val localFragment =
                supportFragmentManager.findFragmentByTag(
                    "f0",
                ) as? com.sza.fastmediasorter.ui.local.LocalFoldersFragment
            localFragment?.let { setupLocalFragmentCallbacks(it) }
        }
        updateButtonsState()
    }

    /**
     * Retrieves the slideshow interval value from the input field.
     * Validates that the value is within the acceptable range (1-300 seconds).
     *
     * @return The interval value if valid, null otherwise
     */
    private fun getIntervalFromInput(): Int? {
        return binding
            .intervalInput
            .text
            .toString()
            .toIntOrNull()
            ?.takeIf { it in 1..300 }
    }

    /**
     * Updates the slideshow interval for a specific connection configuration.
     * Retrieves the current configuration from the database and updates it if the interval has changed.
     *
     * @param configId The ID of the connection configuration to update
     * @return The updated ConnectionConfig if changes were made, null if config not found
     */
    private suspend fun updateConfigInterval(configId: Long): ConnectionConfig? {
        // Fetch config from database
        val config = viewModel.getConfigById(configId) ?: return null
        val interval = getIntervalFromInput() ?: config.interval

        return if (config.interval != interval) {
            val updatedConfig = config.copy(interval = interval)
            viewModel.updateConfig(updatedConfig)
            updatedConfig
        } else {
            config
        }
    }

    private fun setupNetworkFragmentCallbacks(fragment: NetworkFragment) {
        /**
         * Sets up callbacks for the NetworkFragment to handle configuration selection and double-click events.
         * Updates the current configuration state and UI when network folders are selected.
         *
         * @param fragment The NetworkFragment instance to configure
         */
        fragment.onConfigSelected = { config ->
            currentConfigId = config.id
            currentConfig = config
// Only update interval field if empty or invalid, don't overwrite user's input
            val currentInterval =
                binding
                    .intervalInput
                    .text
                    .toString()
                    .toIntOrNull()
            if (currentInterval == null || currentInterval !in 1..300) {
                binding.intervalInput.setText(config.interval.toString())
            }
            updateButtonsState()
        }
        fragment.onConfigDoubleClick = { config ->
            loadConfigAndStartSlideshow(config)
        }
    }

    private fun setupLocalFragmentCallbacks(fragment: com.sza.fastmediasorter.ui.local.LocalFoldersFragment) {
        /**
         * Sets up callbacks for the LocalFoldersFragment to handle folder selection and double-click events.
         * Manages local folder configuration lookup, database operations, and UI updates.
         *
         * @param fragment The LocalFoldersFragment instance to configure
         */
        fragment.onFolderSelected = { folder ->
            lifecycleScope.launch {
                val config =
                    if (folder.isCustom) {
// Custom folders from SCAN or manual selection - stored in DB
                        viewModel.localCustomFolders.value?.find { it.localDisplayName == folder.name }
                    } else {
// Standard folders - search in database directly (not relying on LiveData which may be stale)
                        viewModel.getConfigByName(folder.name)
                    }
                config?.let {
                    currentConfigId = it.id
                    currentConfig = it
// Only update interval field if empty or invalid, don't overwrite user's input
                    val currentInterval =
                        binding
                            .intervalInput
                            .text
                            .toString()
                            .toIntOrNull()
                    if (currentInterval == null || currentInterval !in 1..300) {
                        binding.intervalInput.setText(it.interval.toString())
                    }
                    updateButtonsState()
                } ?: run {
// Config not found - this shouldn't happen for standard folders after ensureStandardLocalFoldersInDatabase
// but could happen for custom folders not yet saved
                    if (!folder.isCustom) {
                        // For standard folders, try to ensure they exist in DB and retry
                        viewModel.ensureStandardLocalFoldersInDatabase()
                        val retryConfig = viewModel.getConfigByName(folder.name)
                        retryConfig?.let {
                            currentConfigId = it.id
                            currentConfig = it
                            val currentInterval =
                                binding
                                    .intervalInput
                                    .text
                                    .toString()
                                    .toIntOrNull()
                            if (currentInterval == null || currentInterval !in 1..300) {
                                binding.intervalInput.setText(it.interval.toString())
                            }
                            updateButtonsState()
                        }
                    }
                }
            }
        }
        fragment.onFolderDoubleClick = { folder ->
            lifecycleScope.launch {
                val config =
                    if (folder.isCustom) {
// Custom folders - use existing DB record
                        viewModel.localCustomFolders.value?.find { it.localDisplayName == folder.name }
                    } else {
// Standard folders - search in database directly
                        viewModel.getConfigByName(folder.name)
                    }
                config?.let {
                    val interval =
                        binding
                            .intervalInput
                            .text
                            .toString()
                            .toIntOrNull() ?: it.interval
                    val updatedConfig = it.copy(interval = interval)
// Update DB for all folders with valid ID
                    if (it.id > 0) {
                        viewModel.updateConfig(updatedConfig)
                    }
                    loadConfigAndStartSlideshow(updatedConfig)
                } ?: run {
// Config not found for standard folder - ensure it exists and retry
                    if (!folder.isCustom) {
                        viewModel.ensureStandardLocalFoldersInDatabase()
                        val retryConfig = viewModel.getConfigByName(folder.name)
                        retryConfig?.let {
                            val interval =
                                binding
                                    .intervalInput
                                    .text
                                    .toString()
                                    .toIntOrNull() ?: it.interval
                            val updatedConfig = it.copy(interval = interval)
                            if (it.id > 0) {
                                viewModel.updateConfig(updatedConfig)
                            }
                            loadConfigAndStartSlideshow(updatedConfig)
                        }
                    }
                }
            }
        }
    }

    private fun tryAutoResumeSession() {
        val lastFolderAddress = preferenceManager.getLastFolderAddress()
        if (lastFolderAddress.isEmpty()) {
            return
        }
        lifecycleScope.launch {
            val config = viewModel.getConfigByFolderAddress(lastFolderAddress)
            if (config != null) {
                loadConfigAndStartSlideshow(config)
            } else {
                preferenceManager.clearLastSession()
            }
        }
    }

    private fun openSettingsForFirstLaunch() {
        val intent =
            Intent(
                this,
                com
                    .sza
                    .fastmediasorter
                    .ui
                    .settings
                    .SettingsActivity::class.java,
            )
        intent.putExtra("initialTab", 0) // Open Sort tab (index 0)
        startActivity(intent)
    }

    private fun restartApplication() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    private fun showRestartDialog() {
        AlertDialog
            .Builder(this)
            .setTitle("Permission Granted")
            .setMessage(
                "Media access permission granted. The app needs to restart to scan your local folders. Restart now?",
            ).setPositiveButton("Restart Now") { _, _ ->
                restartApplication()
            }.setNegativeButton("Restart Later") { dialog, _ ->
                dialog.dismiss()
                // Still open settings since permission was granted
                openSettingsForFirstLaunch()
            }.setCancelable(false)
            .show()
    }

    private fun openMainWithNetworkTab() {
        // Already on main screen, just switch to network tab and show message
        binding.viewPager.setCurrentItem(1, false) // Switch to Network tab
        Toast.makeText(this, "Please add some network folders to get started", Toast.LENGTH_LONG).show()
    }

    private fun requestMediaPermission() {
        val permission =
            if (android
                    .os
                    .Build
                    .VERSION
                    .SDK_INT >=
                android
                    .os
                    .Build
                    .VERSION_CODES
                    .TIRAMISU
            ) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
        requestPermissionLauncher.launch(permission)
    }

    private fun loadConfigAndStartSlideshow(config: ConnectionConfig) {
        // Get current interval from input field (use config.interval as fallback)
        val currentInterval =
            binding
                .intervalInput
                .text
                .toString()
                .toIntOrNull()
                ?.takeIf { it in 1..300 } ?: config.interval

        // Test connection before starting slideshow
        if (config.type != "LOCAL_CUSTOM" && config.type != "LOCAL_STANDARD") {
            testConnectionAndStartSlideshow(config, currentInterval)
            return
        }

        if (config.type == "LOCAL_CUSTOM" || config.type == "LOCAL_STANDARD") {
            preferenceManager.saveLocalFolderSettings(
                config.localUri ?: "",
                config.localDisplayName ?: "",
                currentInterval,
            )
        } else {
            preferenceManager.saveConnectionSettings(
                config.serverAddress,
                config.username,
                config.password,
                config.folderPath,
            )
            preferenceManager.setInterval(currentInterval)
        }
        // Only update lastUsed for DB records (positive ID)
        if (config.id > 0) {
            viewModel.updateLastUsed(config.id)
            // Also update interval in database if it differs
            if (currentInterval != config.interval) {
                viewModel.updateConfigInterval(config.id, currentInterval)
            }
        }
        val intent = Intent(this, SlideshowActivity::class.java)
        intent.putExtra("configId", config.id)
        intent.putExtra("connectionType", if (config.type == "SMB") "SMB" else "LOCAL")
        startActivity(intent)
    }

    private fun testConnectionAndStartSlideshow(
        config: ConnectionConfig,
        currentInterval: Int,
    ) {
        lifecycleScope.launch {
            // Show progress
            val progressDialog =
                androidx
                    .appcompat
                    .app
                    .AlertDialog
                    .Builder(this@MainActivity)
                    .setTitle("Checking Connection")
                    .setMessage("Testing folder accessibility...")
                    .setCancelable(false)
                    .create()
            progressDialog.show()

            try {
                val testResult =
                    withTimeoutOrNull(5000) {
                        withContext(Dispatchers.IO) {
                            val smbClient =
                                com
                                    .sza
                                    .fastmediasorter
                                    .network
                                    .SmbClient()
                            val connected = smbClient.connect(config.serverAddress, config.username, config.password)
                            if (!connected) {
                                throw Exception("Failed to connect to server")
                            }
                            val result = smbClient.getImageFiles(config.serverAddress, config.folderPath, false, 100)
                            if (result.errorMessage != null) {
                                throw Exception(result.errorMessage)
                            }

                            // For connection validation, warnings are OK - only errors should fail
                            // result.warningMessage is informational only

                            // Check write permissions
                            val hasWritePermission =
                                smbClient.checkWritePermission(
                                    config.serverAddress,
                                    config.folderPath,
                                )

                            // Update write permission in database if config has ID
                            if (config.id > 0) {
                                val database =
                                    com
                                        .sza
                                        .fastmediasorter
                                        .data
                                        .AppDatabase
                                        .getDatabase(this@MainActivity)
                                database.connectionConfigDao().updateWritePermission(config.id, hasWritePermission)
                            }

                            hasWritePermission
                        }
                    }

                progressDialog.dismiss()

                if (testResult == null) {
                    throw Exception("Connection timeout (5 seconds). Server or folder may be unreachable.")
                }

                // Success - save settings and start slideshow
                preferenceManager.saveConnectionSettings(
                    config.serverAddress,
                    config.username,
                    config.password,
                    config.folderPath,
                )
                preferenceManager.setInterval(currentInterval)

                if (config.id > 0) {
                    viewModel.updateLastUsed(config.id)
                }

                val intent = Intent(this@MainActivity, SlideshowActivity::class.java)
                intent.putExtra("configId", config.id)
                intent.putExtra("connectionType", if (config.type == "SMB") "SMB" else "LOCAL")
                startActivity(intent)
            } catch (e: Exception) {
                progressDialog.dismiss()
                ErrorDialogHelper.showErrorWithCopy(
                    this@MainActivity,
                    "Connection Failed",
                    "Cannot connect to folder:\n\n${e.message}",
                )
            }
        }
    }

    private fun testConnectionAndStartSort(
        config: ConnectionConfig,
        configId: Long,
    ) {
        // Skip test for local folders
        if (config.type == "LOCAL_CUSTOM" || config.type == "LOCAL_STANDARD") {
            val intent =
                Intent(
                    this,
                    com
                        .sza
                        .fastmediasorter
                        .ui
                        .sort
                        .SortActivity::class.java,
                )
            intent.putExtra("configId", configId)
            startActivity(intent)
            return
        }

        lifecycleScope.launch {
            // Show progress
            val progressDialog =
                androidx
                    .appcompat
                    .app
                    .AlertDialog
                    .Builder(this@MainActivity)
                    .setTitle("Checking Connection")
                    .setMessage("Testing folder accessibility...")
                    .setCancelable(false)
                    .create()
            progressDialog.show()

            try {
                val testResult =
                    withTimeoutOrNull(5000) {
                        withContext(Dispatchers.IO) {
                            val smbClient =
                                com
                                    .sza
                                    .fastmediasorter
                                    .network
                                    .SmbClient()
                            val connected = smbClient.connect(config.serverAddress, config.username, config.password)
                            if (!connected) {
                                throw Exception("Failed to connect to server")
                            }
                            val result = smbClient.getImageFiles(config.serverAddress, config.folderPath, false, 100)
                            if (result.errorMessage != null) {
                                throw Exception(result.errorMessage)
                            }

                            // For connection validation, warnings are OK - only errors should fail
                            // result.warningMessage is informational only

                            // Check write permissions
                            val hasWritePermission =
                                smbClient.checkWritePermission(
                                    config.serverAddress,
                                    config.folderPath,
                                )

                            // Update write permission in database
                            val database =
                                com
                                    .sza
                                    .fastmediasorter
                                    .data
                                    .AppDatabase
                                    .getDatabase(this@MainActivity)
                            database.connectionConfigDao().updateWritePermission(configId, hasWritePermission)

                            hasWritePermission
                        }
                    }

                progressDialog.dismiss()

                if (testResult == null) {
                    throw Exception("Connection timeout (5 seconds). Server or folder may be unreachable.")
                }

                // Success - start sort activity
                val intent =
                    Intent(
                        this@MainActivity,
                        com
                            .sza
                            .fastmediasorter
                            .ui
                            .sort
                            .SortActivity::class.java,
                    )
                intent.putExtra("configId", configId)
                startActivity(intent)
            } catch (e: Exception) {
                progressDialog.dismiss()
                ErrorDialogHelper.showErrorWithCopy(
                    this@MainActivity,
                    "Connection Failed",
                    "Cannot connect to folder:\n\n${e.message}",
                )
            }
        }
    }

    private fun setupClickListeners() {
        /**
         * Sets up click listeners for all interactive UI elements in the main activity.
         * Configures listeners for interval input, slideshow button, sort button, and settings button.
         * Save interval on text change.
         */
        binding.intervalInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {}

                override fun afterTextChanged(s: android.text.Editable?) {
                    val interval = s.toString().toIntOrNull()
                    if (interval != null && interval in 1..300) {
                        currentConfigId?.let { configId ->
                            lifecycleScope.launch {
                                updateConfigInterval(configId)
                            }
                        }
                    }
                }
            },
        )

        binding.slideshowButton.setOnClickListener {
            currentConfigId?.let { configId ->
                lifecycleScope.launch {
                    val config = updateConfigInterval(configId)
                    if (config != null) {
                        loadConfigAndStartSlideshow(config)
                    } else {
                        Toast
                            .makeText(this@MainActivity, getString(R.string.select_connection_first), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            } ?: run {
                Toast.makeText(this, getString(R.string.select_connection_first), Toast.LENGTH_SHORT).show()
            }
        }
        binding.sortButton.setOnClickListener {
            currentConfigId?.let { configId ->
                lifecycleScope.launch {
                    val config = updateConfigInterval(configId)
                    if (config != null) {
                        // Check if sort is possible (has destinations or deletion allowed)
                        val hasDestinations = viewModel.getSortDestinationsCount() > 0
                        val deletionAllowed = preferenceManager.isAllowDelete()
                        if (hasDestinations || deletionAllowed) {
                            testConnectionAndStartSort(config, configId)
                        } else {
                            Toast
                                .makeText(
                                    this@MainActivity,
                                    "Please add sort destinations in Settings or enable deletion",
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    } else {
                        Toast
                            .makeText(
                                this@MainActivity,
                                getString(R.string.select_connection_first),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            } ?: run {
                Toast.makeText(this, getString(R.string.select_connection_first), Toast.LENGTH_SHORT).show()
            }
        }
        binding.settingsButton.setOnClickListener {
            val intent =
                Intent(
                    this,
                    com
                        .sza
                        .fastmediasorter
                        .ui
                        .settings
                        .SettingsActivity::class.java,
                )
            startActivity(intent)
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            updateButtonsState()
        }
    }

    private fun updateButtonsState() {
        val isEnabled = currentConfigId != null
        binding.slideshowButton.isEnabled = isEnabled
        // Sort button enabled if folder selected (destinations check moved to click handler)
        binding.sortButton.isEnabled = isEnabled
    }
}
