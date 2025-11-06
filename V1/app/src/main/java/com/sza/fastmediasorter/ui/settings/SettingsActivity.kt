package com.sza.fastmediasorter.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.ConnectionConfig
import com.sza.fastmediasorter.databinding.ActivitySettingsBinding
import com.sza.fastmediasorter.databinding.FragmentSettingsBinding
import com.sza.fastmediasorter.databinding.FragmentSlideshowHelpBinding
import com.sza.fastmediasorter.ui.ConnectionViewModel
import com.sza.fastmediasorter.ui.base.LocaleActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import com.sza.fastmediasorter.utils.Logger
import com.sza.fastmediasorter.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsActivity : LocaleActivity() {
    private lateinit var binding: ActivitySettingsBinding

    companion object {
        private const val TAG = "SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.d(TAG, "onCreate() called")
        try {
            super.onCreate(savedInstanceState)
            Logger.d(TAG, "super.onCreate() completed")

            binding = ActivitySettingsBinding.inflate(layoutInflater)
            Logger.d(TAG, "binding inflated")

            setContentView(binding.root)
            Logger.d(TAG, "setContentView() completed")

            supportActionBar?.hide()
            Logger.d(TAG, "actionBar hidden")

            setupToolbar()
            Logger.d(TAG, "setupToolbar() completed")

            setupTabs()
            Logger.d(TAG, "setupTabs() completed")

            // Check for initial tab from intent
            val initialTab = intent.getIntExtra("initialTab", 0)
            if (initialTab in 0..2) {
                binding.viewPager.setCurrentItem(initialTab, false)
                Logger.d(TAG, "initial tab set to $initialTab")
            }

            Logger.d(TAG, "onCreate() completed successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "onCreate() crashed", e)
            throw e
        }
    }

    private fun setupToolbar() {
        Logger.d(TAG, "setupToolbar() called")
        try {
            binding.backButton.setOnClickListener {
                Logger.d(TAG, "back button clicked")
                onBackPressed()
            }
            Logger.d(TAG, "setupToolbar() completed")
        } catch (e: Exception) {
            Logger.e(TAG, "setupToolbar() crashed", e)
            throw e
        }
    }

    private fun setupTabs() {
        Logger.d(TAG, "setupTabs() called")
        try {
            val adapter = SettingsPagerAdapter(this)
            Logger.d(TAG, "adapter created")

            binding.viewPager.adapter = adapter
            Logger.d(TAG, "adapter set to viewPager")

            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text =
                    when (position) {
                        0 -> getString(R.string.tab_sort_to)
                        1 -> getString(R.string.tab_slideshow)
                        2 -> getString(R.string.tab_video)
                        3 -> getString(R.string.tab_settings)
                        else -> ""
                    }
            }.attach()
            Logger.d(TAG, "TabLayoutMediator attached")

            Logger.d(TAG, "setupTabs() completed")
        } catch (e: Exception) {
            Logger.e(TAG, "setupTabs() crashed", e)
            throw e
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Logger.d(TAG, "onBackPressed() called")
        super.onBackPressed()
        finish()
    }
}

class SettingsPagerAdapter(
    activity: LocaleActivity,
) : FragmentStateAdapter(activity) {
    companion object {
        private const val TAG = "SettingsPagerAdapter"
    }

    override fun getItemCount(): Int {
        Logger.d(TAG, "getItemCount() called, returning 4")
        return 4
    }

    override fun createFragment(position: Int): Fragment {
        Logger.d(TAG, "createFragment() called for position $position")
        return try {
            val fragment =
                when (position) {
                    0 -> SortContainerFragment()
                    1 -> SlideshowHelpFragment()
                    2 -> VideoSettingsFragment()
                    3 -> SettingsFragment()
                    else -> SortContainerFragment()
                }
            Logger.d(TAG, "createFragment() returning ${fragment.javaClass.simpleName}")
            fragment
        } catch (e: Exception) {
            Logger.e(TAG, "createFragment() crashed for position $position", e)
            throw e
        }
    }
}

class SlideshowHelpFragment : Fragment() {
    private var _binding: FragmentSlideshowHelpBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSlideshowHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())

        binding.showControlsCheckbox.isChecked = preferenceManager.isShowControls()

        binding.showControlsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setShowControls(isChecked)
            updateVisibility()
        }

        updateVisibility()
    }

    override fun onResume() {
        super.onResume()
        updateVisibility()
    }

    private fun updateVisibility() {
        val showControls = preferenceManager.isShowControls()
        binding.touchZonesContainer.visibility = if (showControls) View.GONE else View.VISIBLE
        binding.buttonControlsInfo.visibility = if (showControls) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SortContainerFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_sort_container, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.sortSubViewPager)
        val tabLayout = view.findViewById<com.google.android.material.tabs.TabLayout>(R.id.sortSubTabLayout)

        val adapter = SortSubPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text =
                when (position) {
                    0 -> getString(R.string.subtab_destinations)
                    1 -> getString(R.string.subtab_sort_settings)
                    else -> ""
                }
        }.attach()
    }
}

class SortSubPagerAdapter(
    fragment: Fragment,
) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SortDestinationsFragment()
            1 -> SortSettingsFragment()
            else -> SortDestinationsFragment()
        }
    }
}

class SortDestinationsFragment : Fragment() {
    private lateinit var viewModel: ConnectionViewModel
    private lateinit var adapter: SortDestinationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_sort_destinations, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[ConnectionViewModel::class.java]

        val destinationsRecyclerView = view.findViewById<RecyclerView>(R.id.destinationsRecyclerView)
        val addDestinationButton = view.findViewById<Button>(R.id.addDestinationButton)
        val emptyText = view.findViewById<TextView>(R.id.emptyText)

        setupRecyclerView(destinationsRecyclerView)
        setupObservers(destinationsRecyclerView, emptyText)

        addDestinationButton.setOnClickListener {
            showAddDestinationDialog()
        }
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        adapter =
            SortDestinationAdapter(
                onMoveUp = { config -> moveDestination(config, -1) },
                onMoveDown = { config -> moveDestination(config, 1) },
                onDelete = { config -> deleteDestination(config) },
            )

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupObservers(
        recyclerView: RecyclerView,
        emptyText: TextView,
    ) {
        viewModel.sortDestinations.observe(viewLifecycleOwner) { destinations ->
            adapter.submitList(destinations)
            emptyText.visibility = if (destinations.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (destinations.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showAddDestinationDialog() {
        val dialogView =
            LayoutInflater
                .from(requireContext())
                .inflate(R.layout.dialog_add_sort_destination, null)

        val dialog =
            AlertDialog
                .Builder(requireContext())
                .setView(dialogView)
                .create()

        val connectionsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.connectionsRecyclerView)
        val sortNameInput = dialogView.findViewById<EditText>(R.id.sortNameInput)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val addButton = dialogView.findViewById<Button>(R.id.addButton)

        var selectedConfig: ConnectionConfig? = null

        val connectionAdapter =
            object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                private var connections = listOf<ConnectionConfig>()

                fun setConnections(list: List<ConnectionConfig>) {
                    connections = list
                    notifyDataSetChanged()
                }

                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int,
                ): RecyclerView.ViewHolder {
                    val textView =
                        TextView(parent.context).apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                )
                            setPadding(16, 16, 16, 16)
                            textSize = 14f
                            setTextColor(0xFF000000.toInt()) // Black text for contrast
                        }
                    return object : RecyclerView.ViewHolder(textView) {}
                }

                override fun onBindViewHolder(
                    holder: RecyclerView.ViewHolder,
                    position: Int,
                ) {
                    val config = connections[position]
                    val textView = holder.itemView as TextView
                    val details =
                        when (config.type) {
                            "LOCAL_CUSTOM", "LOCAL_STANDARD" -> "Local: ${config.localDisplayName ?: config.name}"
                            else -> "${config.serverAddress}\\${config.folderPath}"
                        }
                    textView.text = "${config.name}\n$details"

                    // Set colors based on selection
                    if (selectedConfig?.id == config.id) {
                        textView.setBackgroundColor(0xFF6200EE.toInt()) // Purple background when selected
                        textView.setTextColor(0xFFFFFFFF.toInt()) // White text when selected
                    } else {
                        textView.setBackgroundColor(0xFFFFFFFF.toInt()) // White background when not selected
                        textView.setTextColor(0xFF000000.toInt()) // Black text when not selected
                    }

                    textView.setOnClickListener {
                        selectedConfig = config

                        // Auto-fill short name for local folders
                        if (config.type == "LOCAL_CUSTOM" || config.type == "LOCAL_STANDARD") {
                            sortNameInput.setText(config.localDisplayName ?: config.name)
                        }

                        notifyDataSetChanged()
                        addButton.isEnabled = sortNameInput.text.isNotEmpty()
                    }
                }

                override fun getItemCount() = connections.size
            }

        connectionsRecyclerView.adapter = connectionAdapter
        connectionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        viewModel.allConfigs.observe(viewLifecycleOwner) { configs ->
            viewModel.sortDestinations.value?.let { destinations ->
                val usedIds = destinations.map { it.id }.toSet()
                val usedNames = destinations.mapNotNull { it.localDisplayName ?: it.name }.toSet()
                val available =
                    configs
                        .filter { config ->
                            config.id !in usedIds && (config.localDisplayName ?: config.name) !in usedNames
                        }.distinctBy { it.localDisplayName ?: it.name } // Remove duplicates by name
                connectionAdapter.setConnections(available)
            }
        }

        sortNameInput.addTextChangedListener(
            object : TextWatcher {
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

                override fun afterTextChanged(s: Editable?) {
                    addButton.isEnabled = s?.isNotEmpty() == true && selectedConfig != null
                }
            },
        )

        cancelButton.setOnClickListener { dialog.dismiss() }

        addButton.setOnClickListener {
            selectedConfig?.let { config ->
                val sortName = sortNameInput.text.toString().trim()
                if (sortName.isNotEmpty()) {
                    viewModel.addSortDestination(config.id, sortName)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun moveDestination(
        config: ConnectionConfig,
        direction: Int,
    ) {
        viewModel.moveSortDestination(config, direction)
    }

    private fun deleteDestination(config: ConnectionConfig) {
        viewModel.removeSortDestination(config.id)
    }
}

class SortSettingsFragment : Fragment() {
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_sort_settings, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        val allowCopyCheckbox = view.findViewById<android.widget.CheckBox>(R.id.allowCopyCheckbox)
        val goNextAfterCopyCheckbox = view.findViewById<android.widget.CheckBox>(R.id.goNextAfterCopyCheckbox)
        val allowMoveCheckbox = view.findViewById<android.widget.CheckBox>(R.id.allowMoveCheckbox)
        val allowDeleteCheckbox = view.findViewById<android.widget.CheckBox>(R.id.allowDeleteCheckbox)
        val confirmDeleteCheckbox = view.findViewById<android.widget.CheckBox>(R.id.confirmDeleteCheckbox)
        val allowRenameCheckbox = view.findViewById<android.widget.CheckBox>(R.id.allowRenameCheckbox)
        val useSmallButtonsCheckbox = view.findViewById<android.widget.CheckBox>(R.id.useSmallButtonsCheckbox)

        allowCopyCheckbox.isChecked = preferenceManager.isAllowCopy()
        goNextAfterCopyCheckbox.isChecked = preferenceManager.isGoNextAfterCopy()
        allowMoveCheckbox.isChecked = preferenceManager.isAllowMove()
        allowDeleteCheckbox.isChecked = preferenceManager.isAllowDelete()
        confirmDeleteCheckbox.isChecked = preferenceManager.isConfirmDelete()
        allowRenameCheckbox.isChecked = preferenceManager.isAllowRename()
        useSmallButtonsCheckbox.isChecked = preferenceManager.isUseSmallButtons()

        allowCopyCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setAllowCopy(isChecked)
        }

        goNextAfterCopyCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setGoNextAfterCopy(isChecked)
        }

        allowMoveCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setAllowMove(isChecked)
        }

        allowDeleteCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setAllowDelete(isChecked)
        }

        confirmDeleteCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setConfirmDelete(isChecked)
        }

        allowRenameCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setAllowRename(isChecked)
        }

        useSmallButtonsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setUseSmallButtons(isChecked)
        }
    }
}

class VideoSettingsFragment : Fragment() {
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var videoEnabledCheckbox: android.widget.CheckBox
    private lateinit var maxVideoSizeEdit: EditText
    private lateinit var showVideoErrorDetailsCheckbox: android.widget.CheckBox
    private lateinit var playVideoTillEndCheckbox: android.widget.CheckBox

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_video_settings, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        preferenceManager = PreferenceManager(requireContext())

        videoEnabledCheckbox = view.findViewById(R.id.videoEnabledCheckbox)
        maxVideoSizeEdit = view.findViewById(R.id.maxVideoSizeEdit)
        showVideoErrorDetailsCheckbox = view.findViewById(R.id.showVideoErrorDetailsCheckbox)
        playVideoTillEndCheckbox = view.findViewById(R.id.playVideoTillEndCheckbox)

        // Load current settings
        videoEnabledCheckbox.isChecked = preferenceManager.isVideoEnabled()
        maxVideoSizeEdit.setText(preferenceManager.getMaxVideoSizeMb().toString())
        maxVideoSizeEdit.isEnabled = videoEnabledCheckbox.isChecked
        showVideoErrorDetailsCheckbox.isChecked = preferenceManager.isShowVideoErrorDetails()
        playVideoTillEndCheckbox.isChecked = preferenceManager.isPlayVideoTillEnd()
        playVideoTillEndCheckbox.isEnabled = videoEnabledCheckbox.isChecked

        // Enable/disable size field based on checkbox
        videoEnabledCheckbox.setOnCheckedChangeListener { _, isChecked ->
            maxVideoSizeEdit.isEnabled = isChecked
            playVideoTillEndCheckbox.isEnabled = isChecked
            preferenceManager.setVideoEnabled(isChecked)
        }

        // Save error details checkbox
        showVideoErrorDetailsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setShowVideoErrorDetails(isChecked)
        }

        // Save play video till end checkbox
        playVideoTillEndCheckbox.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setPlayVideoTillEnd(isChecked)
        }

        // Validate and save video size
        maxVideoSizeEdit.addTextChangedListener(
            object : TextWatcher {
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

                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (text.isNotEmpty()) {
                        try {
                            val value = text.toInt()
                            if (value in 1..999999) {
                                preferenceManager.setMaxVideoSizeMb(value)
                            } else {
                                // Show error hint
                                maxVideoSizeEdit.error = "Range: 1-999999 MB"
                            }
                        } catch (e: NumberFormatException) {
                            maxVideoSizeEdit.error = "Invalid number"
                        }
                    }
                }
            },
        )
    }
}

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: PreferenceManager

    companion object {
        private const val TAG = "SettingsFragment"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            androidx
                .activity
                .result
                .contract
                .ActivityResultContracts
                .RequestPermission(),
        ) { isGranted ->
            Logger.d(TAG, "permission result: isGranted=$isGranted")
            updateMediaAccessButton()
            if (isGranted) {
                // Show restart dialog after granting permission
                showRestartDialog()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Logger.d(TAG, "onCreateView() called")
        try {
            _binding = FragmentSettingsBinding.inflate(inflater, container, false)
            Logger.d(TAG, "binding inflated")
            return binding.root
        } catch (e: Exception) {
            Logger.e(TAG, "onCreateView() crashed", e)
            throw e
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        Logger.d(TAG, "onViewCreated() called")
        try {
            super.onViewCreated(view, savedInstanceState)
            Logger.d(TAG, "super.onViewCreated() completed")

            preferenceManager = PreferenceManager(requireContext())
            Logger.d(TAG, "PreferenceManager created")

            // Load default credentials
            binding.defaultUsernameInput.setText(preferenceManager.getDefaultUsername())
            Logger.d(TAG, "username loaded")

            binding.defaultPasswordInput.setText(preferenceManager.getDefaultPassword())
            Logger.d(TAG, "password loaded")

            // Load keep screen on setting
            binding.keepScreenOnCheckbox.isChecked = preferenceManager.isKeepScreenOn()
            Logger.d(TAG, "keepScreenOn loaded")

            // Setup language spinner
            setupLanguageSpinner()
            Logger.d(TAG, "language spinner setup completed")

            // Setup User Guide button
            binding.userGuideButton.setOnClickListener {
                Logger.d(TAG, "userGuide button clicked")
                val intent = Intent(requireContext(), WelcomeActivity::class.java)
                intent.putExtra("isFirstLaunch", false)
                startActivity(intent)
            }
            Logger.d(TAG, "userGuide button setup completed")

            // Setup View Logs button
            binding.viewLogsButton.setOnClickListener {
                Logger.d(TAG, "viewLogs button clicked")
                showLogsDialog()
            }
            Logger.d(TAG, "viewLogs button setup completed")

            // Setup View Session Logs button
            binding.viewSessionLogsButton.setOnClickListener {
                Logger.d(TAG, "viewSessionLogs button clicked")
                showSessionLogsDialog()
            }
            Logger.d(TAG, "viewSessionLogs button setup completed")

            // Setup media access button
            updateMediaAccessButton()
            Logger.d(TAG, "media access button updated")

            binding.requestMediaAccessButton.setOnClickListener {
                Logger.d(TAG, "requestMediaAccess button clicked")
                requestMediaPermission()
            }
            Logger.d(TAG, "requestMediaAccess button setup completed")

            // Save on focus loss
            binding.defaultUsernameInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveDefaultUsername()
                }
            }

            binding.defaultPasswordInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveDefaultPassword()
                }
            }
            Logger.d(TAG, "focus listeners setup completed")

            // Save keep screen on on change
            binding.keepScreenOnCheckbox.setOnCheckedChangeListener { _, isChecked ->
                Logger.d(TAG, "keepScreenOn changed to $isChecked")
                preferenceManager.setKeepScreenOn(isChecked)
            }
            Logger.d(TAG, "keepScreenOn listener setup completed")

            Logger.d(TAG, "onViewCreated() completed successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "onViewCreated() crashed", e)
            throw e
        }
    }

    private fun updateMediaAccessButton() {
        val hasPermission =
            com
                .sza
                .fastmediasorter
                .network
                .LocalStorageClient
                .hasMediaPermission(requireContext())
        binding.requestMediaAccessButton.isEnabled = !hasPermission
        binding.requestMediaAccessButton.text = if (hasPermission) "Media Access Granted ✓" else "Grant Media Access"
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

    private fun saveDefaultUsername() {
        val username =
            binding
                .defaultUsernameInput
                .text
                .toString()
                .trim()
        preferenceManager.setDefaultUsername(username)
    }

    private fun saveDefaultPassword() {
        val password =
            binding
                .defaultPasswordInput
                .text
                .toString()
                .trim()
        preferenceManager.setDefaultPassword(password)
    }

    private fun showRestartDialog() {
        AlertDialog
            .Builder(requireContext())
            .setTitle(getString(R.string.permission_granted_title))
            .setMessage(getString(R.string.permission_granted_message))
            .setPositiveButton(getString(R.string.restart_now)) { _, _ ->
                restartApplication()
            }.setNegativeButton(getString(R.string.restart_later)) { dialog, _ ->
                dialog.dismiss()
            }.setCancelable(false)
            .show()
    }

    private fun restartApplication() {
        val intent = requireActivity().packageManager.getLaunchIntentForPackage(requireActivity().packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        requireActivity().startActivity(intent)
        requireActivity().finishAffinity()
    }

    private fun showLogsDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logs = getLast1000LogLines()

            withContext(Dispatchers.Main) {
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(getString(R.string.logs_title))
                    .setMessage(logs.ifEmpty { getString(R.string.logs_empty) })
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setNeutralButton(getString(R.string.copy_logs)) { _, _ ->
                        copyLogsToClipboard(logs)
                    }.show()
            }
        }
    }

    private fun showSessionLogsDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logs = getSessionLogLines()

            withContext(Dispatchers.Main) {
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(getString(R.string.session_logs_title))
                    .setMessage(logs.ifEmpty { getString(R.string.logs_empty) })
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setNeutralButton(getString(R.string.copy_logs)) { _, _ ->
                        copyLogsToClipboard(logs)
                    }.show()
            }
        }
    }

    private fun getLast1000LogLines(): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            val allLines = bufferedReader.readLines()
            val appLines =
                allLines.filter { line ->
                    line.contains("fastmediasorter", ignoreCase = true) ||
                        line.contains("FastMediaSorter", ignoreCase = true) ||
                        line.contains("SortActivity", ignoreCase = true) ||
                        line.contains("SlideshowActivity", ignoreCase = true) ||
                        line.contains("MainActivity", ignoreCase = true) ||
                        line.contains("SmbClient", ignoreCase = true) ||
                        line.contains("com.sza.fastmediasorter", ignoreCase = true)
                }

            val last1000 =
                if (appLines.size > 1000) {
                    appLines.takeLast(1000)
                } else {
                    appLines
                }

            last1000.joinToString("\n")
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    private fun getSessionLogLines(): String {
        return try {
            val sessionStartTime =
                com
                    .sza
                    .fastmediasorter
                    .FastMediaSorterApplication
                    .sessionStartTime

            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            val allLines = bufferedReader.readLines()

            // Filter by app name and session time - include logs from last 3 sessions (approximately)
            val appLines =
                allLines.filter { line ->
                    // Check if line contains app-related tags
                    val isAppRelated =
                        line.contains("fastmediasorter", ignoreCase = true) ||
                            line.contains("FastMediaSorter", ignoreCase = true) ||
                            line.contains("SortActivity", ignoreCase = true) ||
                            line.contains("SlideshowActivity", ignoreCase = true) ||
                            line.contains("MainActivity", ignoreCase = true) ||
                            line.contains("SmbClient", ignoreCase = true) ||
                            line.contains("com.sza.fastmediasorter", ignoreCase = true)

                    if (!isAppRelated) return@filter false

                    // Parse timestamp from logcat format: "10-19 20:34:31.368"
                    val timestampMatch = Regex("""(\d{2})-(\d{2})\s+(\d{2}):(\d{2}):(\d{2})\.(\d{3})""").find(line)
                    if (timestampMatch != null) {
                        try {
                            val (month, day, hour, minute, second, millis) = timestampMatch.destructured

                            // Create calendar for log line timestamp (assuming current year)
                            val logCalendar = java.util.Calendar.getInstance()
                            logCalendar.set(java.util.Calendar.MONTH, month.toInt() - 1)
                            logCalendar.set(java.util.Calendar.DAY_OF_MONTH, day.toInt())
                            logCalendar.set(java.util.Calendar.HOUR_OF_DAY, hour.toInt())
                            logCalendar.set(java.util.Calendar.MINUTE, minute.toInt())
                            logCalendar.set(java.util.Calendar.SECOND, second.toInt())
                            logCalendar.set(java.util.Calendar.MILLISECOND, millis.toInt())

                            val logTimestamp = logCalendar.timeInMillis

                            // Include logs from last 3 sessions (approximately 3 hours back)
                            // This is a rough approximation since we don't store previous session times
                            val threeHoursAgo = sessionStartTime - (3 * 60 * 60 * 1000)
                            return@filter logTimestamp >= threeHoursAgo
                        } catch (e: Exception) {
                            // If parsing fails, include the line
                            return@filter true
                        }
                    }

                    // If no timestamp found, include the line
                    true
                }

            // Take last 512 lines from recent sessions (instead of 1000)
            val last512 =
                if (appLines.size > 512) {
                    appLines.takeLast(512)
                } else {
                    appLines
                }

            last512.joinToString("\n")
        } catch (e: Exception) {
            "Error reading session logs: ${e.message}"
        }
    }

    private fun copyLogsToClipboard(logs: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Application Logs", logs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
    }

    private fun setupLanguageSpinner() {
        val languageEntries = resources.getStringArray(R.array.language_entries)
        val languageValues = resources.getStringArray(R.array.language_values)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, languageEntries)
        binding.languageSpinner.setAdapter(adapter)

        // Set current language
        val currentLanguage = preferenceManager.getLanguage()
        val currentIndex = languageValues.indexOf(currentLanguage)
        if (currentIndex >= 0) {
            binding.languageSpinner.setText(languageEntries[currentIndex], false)
        }

        // Handle language selection
        binding.languageSpinner.setOnItemClickListener { _, _, position, _ ->
            val selectedLanguage = languageValues[position]
            if (selectedLanguage != preferenceManager.getLanguage()) {
                preferenceManager.setLanguage(selectedLanguage)

                // Restart activity to apply changes to all screens
                requireActivity().recreate()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Save when leaving fragment (tab change, back)
        saveDefaultUsername()
        saveDefaultPassword()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
