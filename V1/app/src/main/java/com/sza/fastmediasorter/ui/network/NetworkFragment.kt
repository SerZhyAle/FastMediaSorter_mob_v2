package com.sza.fastmediasorter.ui.network

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.ConnectionConfig
import com.sza.fastmediasorter.ui.ConnectionAdapter
import com.sza.fastmediasorter.ui.ConnectionViewModel
import com.sza.fastmediasorter.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NetworkFragment : Fragment() {
    private val viewModel: ConnectionViewModel by activityViewModels()
    private lateinit var adapter: ConnectionAdapter
    private lateinit var rvConnections: RecyclerView
    private lateinit var etFolderAddress: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etName: TextInputEditText
    private lateinit var cbAddToDestinations: CheckBox
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var btnScan: Button
    private var selectedConfig: ConnectionConfig? = null
    var onConfigSelected: ((ConnectionConfig) -> Unit)? = null
    var onConfigDoubleClick: ((ConnectionConfig) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_network, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        rvConnections = view.findViewById(R.id.connectionsRecyclerView)
        etFolderAddress = view.findViewById(R.id.folderAddressInput)
        etUsername = view.findViewById(R.id.usernameInput)
        etPassword = view.findViewById(R.id.passwordInput)
        etName = view.findViewById(R.id.nameInput)
        cbAddToDestinations = view.findViewById(R.id.addToDestinationsCheckbox)
        btnSave = view.findViewById(R.id.saveButton)
        btnTest = view.findViewById(R.id.testButton)
        btnScan = view.findViewById(R.id.scanButton)
        setupRecyclerView()
        observeConnections()
        setupSaveButton()
        setupTestButton()
        setupScanButton()
    }

    private fun setupRecyclerView() {
        adapter =
            ConnectionAdapter(
                onItemClick = { config ->
                    selectedConfig = config
                    loadConfig(config)
                    onConfigSelected?.invoke(config)
                },
                onItemDoubleClick = { config ->
                    onConfigDoubleClick?.invoke(config)
                },
                onDeleteClick = { config ->
                    if (selectedConfig?.id == config.id) {
                        selectedConfig = null
                        clearInputs()
                    }
                    viewModel.deleteConfig(config)
                    Toast.makeText(requireContext(), R.string.connection_deleted, Toast.LENGTH_SHORT).show()
                },
            )
        rvConnections.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NetworkFragment.adapter
        }

        // Set default template for folder address based on current subnet
        if (etFolderAddress.text.isNullOrEmpty()) {
            val defaultTemplate = getDefaultTemplate()
            etFolderAddress.setText(defaultTemplate)
            etFolderAddress.setSelection(etFolderAddress.text?.length?.minus(1) ?: 0)
        }

        // Update hint with current subnet info
        updateFolderAddressHint()

        // Auto-correct address on focus change
        etFolderAddress.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = etFolderAddress.text.toString()
                val corrected = text.replace(",", ".").replace("/", "\\")
                if (corrected != text) {
                    etFolderAddress.setText(corrected)
                }

                // Auto-generate name from folder path
                if (etName.text.isNullOrEmpty()) {
                    val folderName = corrected.substringAfterLast("\\", "").trim()
                    if (folderName.isNotEmpty()) {
                        etName.setText(folderName)
                    }
                }
            }
        }

        // Set default checkbox state
        cbAddToDestinations.isChecked = true
    }

    private fun observeConnections() {
        viewModel.allConfigs.observe(viewLifecycleOwner) { configs ->
            val smbConfigs = configs.filter { it.type == "SMB" }
            adapter.submitList(smbConfigs) {
// Restore selection after list update
                selectedConfig?.let { selected ->
                    val position = smbConfigs.indexOfFirst { it.id == selected.id }
                    if (position >= 0) {
                        adapter.setSelectedPosition(position)
                    } else {
// Selected config no longer exists
                        selectedConfig = null
                        adapter.clearSelection()
                        clearInputs()
                    }
                }
            }
        }
    }

    private fun setupSaveButton() {
        btnSave.setOnClickListener {
            saveConnection()
        }
    }

    private fun setupTestButton() {
        btnTest.setOnClickListener {
            testConnection()
        }
    }

    private fun testConnection() {
        val folderAddress = etFolderAddress.text.toString().trim()
        var username = etUsername.text.toString().trim()
        var password = etPassword.text.toString().trim()

        if (folderAddress.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter folder address", Toast.LENGTH_SHORT).show()
            return
        }

        val preferenceManager =
            com
                .sza
                .fastmediasorter
                .utils
                .PreferenceManager(requireContext())
        if (username.isEmpty()) {
            username = preferenceManager.getDefaultUsername()
        }
        if (password.isEmpty()) {
            password = preferenceManager.getDefaultPassword()
        }

        val (server, folder) = parseFolderAddress(folderAddress)

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                btnTest.isEnabled = false
                btnTest.text = "Testing..."
            }

            try {
                // Try to resolve server name to IP
                var resolvedServer = server
                var dnsResolved = false

                if (!server.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                    // Not an IP address, try DNS resolution
                    try {
                        val resolved =
                            java
                                .net
                                .InetAddress
                                .getByName(server)
                                .hostAddress
                        if (resolved != null) {
                            resolvedServer = resolved
                            dnsResolved = true
                            Logger.d("NetworkFragment", "DNS resolved '$server' to '$resolved'")
                        }
                    } catch (e: Exception) {
                        // DNS failed, but jCIFS-ng can still try NetBIOS
                        Logger.d(
                            "NetworkFragment",
                            "DNS resolution failed for '$server', will try NetBIOS: ${e.message}",
                        )
                        resolvedServer = server
                        dnsResolved = false
                    }
                }

                val smbClient =
                    com
                        .sza
                        .fastmediasorter
                        .network
                        .SmbClient()
                val connected = smbClient.connect(resolvedServer, username, password)

                if (!connected) {
                    withContext(Dispatchers.Main) {
                        val errorMsg =
                            if (!dnsResolved && !server.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                                "=== CONNECTION FAILED ===\n\n" +
                                    "Server name: '$server'\n" +
                                    "Resolution: DNS lookup failed, NetBIOS also failed\n\n" +
                                    "⚠️ NetBIOS name resolution is unreliable on Android\n\n" +
                                    "RECOMMENDED SOLUTION:\n" +
                                    "Use IP address instead of computer name\n\n" +
                                    "How to find IP address:\n" +
                                    "1. On Windows: Open CMD and type 'ipconfig'\n" +
                                    "2. Look for 'IPv4 Address'\n" +
                                    "3. Use that IP instead of '$server'\n\n" +
                                    "Example: Instead of '$server\\$folder'\n" +
                                    "Use: '192.168.1.100\\$folder'\n\n" +
                                    "Other checks:\n" +
                                    "• Server is powered on?\n" +
                                    "• In the same network?\n" +
                                    "• Firewall allows SMB (port 445)?"
                            } else {
                                "=== CONNECTION FAILED ===\n\n" +
                                    "Server: $resolvedServer\n\n" +
                                    "Connection attempt failed\n\n" +
                                    "Check:\n" +
                                    "• Server IP correct?\n" +
                                    "• Server running?\n" +
                                    "• Firewall allows SMB (port 445)?\n" +
                                    "• Username/password correct?"
                            }

                        com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
                            requireContext(),
                            "Test FAILED",
                            errorMsg,
                            false,
                        )
                        btnTest.isEnabled = true
                        btnTest.text = "Test"
                    }
                    return@launch
                }

                val isVideoEnabled =
                    com
                        .sza
                        .fastmediasorter
                        .utils
                        .PreferenceManager(requireContext())
                        .isVideoEnabled()
                val maxVideoSizeMb =
                    com
                        .sza
                        .fastmediasorter
                        .utils
                        .PreferenceManager(
                            requireContext(),
                        ).getMaxVideoSizeMb()
                val result = smbClient.getImageFiles(resolvedServer, folder, isVideoEnabled, maxVideoSizeMb)

                // Check write permissions
                val hasWritePermission = smbClient.checkWritePermission(resolvedServer, folder)

                withContext(Dispatchers.Main) {
                    if (result.errorMessage != null) {
                        com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
                            requireContext(),
                            "Test FAILED",
                            result.errorMessage,
                            false,
                        )
                    } else {
                        // Check if this is a warning case (no media files)
                        val isWarning = result.warningMessage != null
                        val messageTitle = if (isWarning) "Test PASSED ⚠" else "Test PASSED ✓"

                        // Gather network info
                        val wifiInfo =
                            try {
                                val wifiManager =
                                    requireContext().applicationContext.getSystemService(
                                        android.content.Context.WIFI_SERVICE,
                                    ) as android.net.wifi.WifiManager
                                val info = wifiManager.connectionInfo
                                val ipAddress = info.ipAddress
                                val ip =
                                    String.format(
                                        java.util.Locale.US,
                                        "%d.%d.%d.%d",
                                        ipAddress and 0xff,
                                        ipAddress shr 8 and 0xff,
                                        ipAddress shr 16 and 0xff,
                                        ipAddress shr 24 and 0xff,
                                    )
                                "WiFi: ${info.ssid}\nDevice IP: $ip\nLink Speed: ${info.linkSpeed} Mbps\nRSSI: ${info.rssi} dBm"
                            } catch (e: Exception) {
                                "WiFi info unavailable"
                            }

                        // Count file types
                        val imageCount =
                            result.files.count {
                                it.lowercase().endsWith(".jpg") ||
                                    it.lowercase().endsWith(".jpeg") ||
                                    it.lowercase().endsWith(".png") ||
                                    it.lowercase().endsWith(".gif") ||
                                    it.lowercase().endsWith(".webp") ||
                                    it.lowercase().endsWith(".bmp")
                            }
                        val videoCount =
                            result.files.count {
                                it.lowercase().endsWith(".mp4") ||
                                    it.lowercase().endsWith(".mkv") ||
                                    it.lowercase().endsWith(".mov") ||
                                    it.lowercase().endsWith(".webm") ||
                                    it.lowercase().endsWith(".3gp")
                            }

                        // Determine message type and title
                        val totalMediaFiles = imageCount + videoCount
                        val warningNote =
                            if (result.warningMessage != null) {
                                "⚠ WARNING: ${result.warningMessage}\n\n"
                            } else if (totalMediaFiles == 0) {
                                "⚠ Warning: Folder is accessible but contains no supported media files\n\n"
                            } else {
                                ""
                            }

                        val successMsg =
                            warningNote +
                                "=== SMB CONNECTION TEST SUCCESS ===\n" +
                                "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(
                                    java
                                        .util
                                        .Date(),
                                )}\n\n" +
                                "=== CONNECTION INFO ===\n" +
                                "Server Name: $server\n" +
                                "Resolved IP: $resolvedServer\n" +
                                "Resolution Method: ${if (dnsResolved) {
                                    "DNS"
                                } else if (server.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                                    "Direct IP"
                                } else {
                                    "NetBIOS/Direct"
                                }}\n" +
                                "Folder Path: $folder\n" +
                                "Full Path: smb://$resolvedServer/$folder\n" +
                                "Username: ${if (username.isNotEmpty()) username else "(anonymous)"}\n" +
                                "Auth: ${if (password.isNotEmpty()) "Password provided" else "No password"}\n\n" +
                                "=== TEST RESULTS ===\n" +
                                "${if (dnsResolved) "✓" else "⚠"} DNS Resolution: ${if (dnsResolved) "SUCCESS" else "SKIPPED (NetBIOS used)"}\n" +
                                "✓ SMB Connection: SUCCESS\n" +
                                "✓ Authentication: SUCCESS\n" +
                                "✓ Folder Access: SUCCESS\n" +
                                "✓ Read Permissions: VERIFIED\n" +
                                "${if (hasWritePermission) "✓" else "✗"} Write Permissions: ${if (hasWritePermission) "GRANTED" else "DENIED"}\n\n" +
                                "=== MEDIA DISCOVERY ===\n" +
                                "Total Files: ${result.files.size}\n" +
                                "  • Images: $imageCount\n" +
                                "  • Videos: $videoCount${if (videoCount > 0) " (enabled)" else ""}\n" +
                                "Video Size Limit: $maxVideoSizeMb MB\n" +
                                "${if (result.files.isNotEmpty()) {
                                    "First 5 files:\n${result.files.take(5).joinToString("\n") {
                                        "  - $it"
                                    }}"
                                } else {
                                    "No media files found"
                                }}\n\n" +
                                "=== NETWORK INFO ===\n" +
                                "$wifiInfo\n\n" +
                                "=== DEVICE INFO ===\n" +
                                "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                                "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                                "SDK: ${android.os.Build.VERSION.SDK_INT}\n" +
                                "ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}\n\n" +
                                "=== SMB PROTOCOL ===\n" +
                                "Protocol: SMB 2.0.2 - 3.1.1\n" +
                                "Library: jCIFS-ng\n" +
                                "Security: BouncyCastle Provider\n\n" +
                                "✅ Connection test completed successfully!"

                        com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
                            requireContext(),
                            messageTitle,
                            successMsg,
                            true,
                        )
                    }
                }
            } catch (e: Exception) {
                val diagnostic =
                    "=== UNEXPECTED ERROR IN TEST ===\n" +
                        "Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(
                            java
                                .util
                                .Date(),
                        )}\n\n" +
                        "=== ERROR DETAILS ===\n" +
                        "Exception: ${e.javaClass.simpleName}\n" +
                        "Message: ${e.message ?: "No message"}\n" +
                        "Cause: ${e.cause?.javaClass?.simpleName ?: "None"}\n\n" +
                        "=== STACK TRACE ===\n" +
                        android.util.Log.getStackTraceString(e)

                withContext(Dispatchers.Main) {
                    com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
                        requireContext(),
                        "Test FAILED - Unexpected Error",
                        diagnostic,
                        false,
                    )
                }
            }

            withContext(Dispatchers.Main) {
                btnTest.isEnabled = true
                btnTest.text = "Test"
            }
        }
    }

    private fun loadConfig(config: ConnectionConfig) {
        val folderAddress = "${config.serverAddress}\\${config.folderPath}"
        etFolderAddress.setText(folderAddress)
        etUsername.setText(config.username)
        etPassword.setText(config.password)
        etName.setText(config.name)
    }

    fun clearInputs() {
        etFolderAddress.setText(getDefaultTemplate())
        etFolderAddress.setSelection(etFolderAddress.text?.length?.minus(1) ?: 0)
        etUsername.setText("")
        etPassword.setText("")
        etName.setText("")
        cbAddToDestinations.isChecked = true
        selectedConfig = null
        adapter.clearSelection()
    }

    private fun getDefaultTemplate(): String {
        return try {
            val wifiManager =
                requireContext().applicationContext.getSystemService(
                    android.content.Context.WIFI_SERVICE,
                ) as android.net.wifi.WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress != 0) {
                val deviceIp =
                    String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                    )
                "$deviceIp\\"
            } else {
                "192.168.1.\\"
            }
        } catch (e: Exception) {
            "192.168.1.\\"
        }
    }

    private fun updateFolderAddressHint() {
        try {
            val wifiManager =
                requireContext().applicationContext.getSystemService(
                    android.content.Context.WIFI_SERVICE,
                ) as android.net.wifi.WifiManager
            val info = wifiManager.connectionInfo
            val ipAddress = info.ipAddress
            if (ipAddress != 0) {
                val deviceIp =
                    String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff,
                    )
                val subnet = deviceIp.substringBeforeLast('.')
                view?.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.folderAddressLayout)?.hint =
                    "Current subnet: $subnet.x (e.g. $subnet.100\\photos)"
            }
        } catch (e: Exception) {
            // Keep default hint
        }
    }

    private fun setupScanButton() {
        btnScan.setOnClickListener {
            scanAndAddShares()
        }
    }

    private fun scanAndAddShares() {
        val folderAddress = etFolderAddress.text.toString().trim()
        var username = etUsername.text.toString().trim()
        var password = etPassword.text.toString().trim()

        val preferenceManager =
            com
                .sza
                .fastmediasorter
                .utils
                .PreferenceManager(requireContext())
        if (username.isEmpty()) {
            username = preferenceManager.getDefaultUsername()
        }
        if (password.isEmpty()) {
            password = preferenceManager.getDefaultPassword()
        }

        // Extract server from folderAddress
        val (server, _) = parseFolderAddress(folderAddress)

        if (server.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter server address", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                btnScan.isEnabled = false
                btnScan.text = "Scanning..."
            }

            try {
                // Resolve server name to IP if needed
                var resolvedServer = server
                if (!server.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                    try {
                        val resolved =
                            java
                                .net
                                .InetAddress
                                .getByName(server)
                                .hostAddress
                        if (resolved != null) {
                            resolvedServer = resolved
                        }
                    } catch (e: Exception) {
                        Logger.d("NetworkFragment", "DNS resolution failed, trying direct: ${e.message}")
                    }
                }

                val smbClient =
                    com
                        .sza
                        .fastmediasorter
                        .network
                        .SmbClient()
                val connected = smbClient.connect(resolvedServer, username, password)

                if (!connected) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to connect to server", Toast.LENGTH_SHORT).show()
                        btnScan.isEnabled = true
                        btnScan.text = getString(R.string.scan_and_add_shares)
                    }
                    return@launch
                }

                // Get list of shares from server
                val shares = smbClient.listShares(resolvedServer)

                if (shares.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "No accessible shares found", Toast.LENGTH_SHORT).show()
                        btnScan.isEnabled = true
                        btnScan.text = getString(R.string.scan_and_add_shares)
                    }
                    return@launch
                }

                // Add each share as a connection if not already exists
                var addedCount = 0
                for (share in shares) {
                    val existingConfig = viewModel.getConfigByFolderAddress(resolvedServer, share)
                    if (existingConfig == null) {
                        val config =
                            ConnectionConfig(
                                id = 0,
                                name = "$server\\$share",
                                serverAddress = resolvedServer,
                                username = username,
                                password = password,
                                folderPath = share,
                                interval = 10,
                                lastUsed = System.currentTimeMillis(),
                                type = "SMB",
                                writePermission = true,
                                // SMB folders have write permission
                            )
                        viewModel.insertConfigAndGetId(config)
                        addedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    val message =
                        if (addedCount > 0) {
                            "Added $addedCount new share(s). Found ${shares.size} total."
                        } else {
                            "No new shares added. All ${shares.size} shares already in list."
                        }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    btnScan.isEnabled = true
                    btnScan.text = getString(R.string.scan_and_add_shares)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Scan failed: ${e.message}", Toast.LENGTH_LONG).show()
                    btnScan.isEnabled = true
                    btnScan.text = getString(R.string.scan_and_add_shares)
                }
            }
        }
    }

    private fun saveConnection() {
        val folderAddress = etFolderAddress.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()
        var name = etName.text.toString().trim()
        if (folderAddress.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter folder address", Toast.LENGTH_SHORT).show()
            return
        }
        if (name.isEmpty()) {
            name = folderAddress
        }
        val (server, folder) = parseFolderAddress(folderAddress)
        lifecycleScope.launch {
            val existingConfig = viewModel.getConfigByFolderAddress(server, folder)
            val savedConfig: ConnectionConfig
            if (existingConfig != null) {
                val config =
                    existingConfig.copy(
                        name = name,
                        username = username,
                        password = password,
                        lastUsed = System.currentTimeMillis(),
                    )
                viewModel.updateConfig(config)
                selectedConfig = config
                savedConfig = config
                onConfigSelected?.invoke(config)
                Toast.makeText(requireContext(), "Connection updated", Toast.LENGTH_SHORT).show()
            } else {
                val config =
                    ConnectionConfig(
                        id = 0,
                        name = name,
                        serverAddress = server,
                        username = username,
                        password = password,
                        folderPath = folder,
                        interval = 10,
                        lastUsed = System.currentTimeMillis(),
                        type = "SMB",
                        writePermission = true,
                        // SMB folders have write permission
                    )
                val newId = viewModel.insertConfigAndGetId(config)
                savedConfig = config.copy(id = newId)
                selectedConfig = savedConfig
                onConfigSelected?.invoke(savedConfig)
                Toast.makeText(requireContext(), R.string.connection_saved, Toast.LENGTH_SHORT).show()
            }
            if (cbAddToDestinations.isChecked) {
                val destinations = viewModel.sortDestinations.value ?: emptyList()
                if (destinations.size < 10) {
                    val alreadyExists =
                        destinations.any {
                            it.serverAddress == savedConfig.serverAddress && it.folderPath == savedConfig.folderPath
                        }
                    if (!alreadyExists) {
                        viewModel.addSortDestination(savedConfig.id, savedConfig.name)
                        Toast.makeText(requireContext(), "Added to destinations", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Destinations list is full (max 10)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseFolderAddress(folderAddress: String): Pair<String, String> {
        val normalized = folderAddress.replace("/", "\\").trimStart('\\')
        val parts = normalized.split("\\", limit = 2)
        val server = parts[0]
        val folder = if (parts.size > 1) parts[1] else ""
        return Pair(server, folder)
    }
}
