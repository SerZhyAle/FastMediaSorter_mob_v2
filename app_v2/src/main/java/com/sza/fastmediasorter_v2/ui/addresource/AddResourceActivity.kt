package com.sza.fastmediasorter_v2.ui.addresource

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityAddResourceBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.NetworkInterface

@AndroidEntryPoint
class AddResourceActivity : BaseActivity<ActivityAddResourceBinding>() {

    private val viewModel: AddResourceViewModel by viewModels()
    private lateinit var resourceToAddAdapter: ResourceToAddAdapter
    private lateinit var smbResourceToAddAdapter: ResourceToAddAdapter

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Timber.d("Selected folder: $uri")
            viewModel.addManualFolder(uri)
        }
    }

    override fun getViewBinding(): ActivityAddResourceBinding {
        return ActivityAddResourceBinding.inflate(layoutInflater)
    }

    override fun setupViews() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        setupIpAddressField()

        resourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected ->
                viewModel.toggleResourceSelection(resource, selected)
            },
            onNameChanged = { resource, newName ->
                viewModel.updateResourceName(resource, newName)
            },
            onDestinationChanged = { resource, isDestination ->
                viewModel.toggleDestination(resource, isDestination)
            }
        )
        
        binding.rvResourcesToAdd.adapter = resourceToAddAdapter
        
        smbResourceToAddAdapter = ResourceToAddAdapter(
            onSelectionChanged = { resource, selected ->
                viewModel.toggleResourceSelection(resource, selected)
            },
            onNameChanged = { resource, newName ->
                viewModel.updateResourceName(resource, newName)
            },
            onDestinationChanged = { resource, isDestination ->
                viewModel.toggleDestination(resource, isDestination)
            }
        )
        
        binding.rvSmbResourcesToAdd.adapter = smbResourceToAddAdapter

        binding.cardLocalFolder.setOnClickListener {
            showLocalFolderOptions()
        }

        binding.cardNetworkFolder.setOnClickListener {
            showSmbFolderOptions()
        }

        binding.btnScan.setOnClickListener {
            viewModel.scanLocalFolders()
        }

        binding.btnAddManually.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        binding.btnAddToResources.setOnClickListener {
            viewModel.addSelectedResources()
        }

        // SMB buttons
        binding.btnSmbTest.setOnClickListener {
            testSmbConnection()
        }

        binding.btnSmbScan.setOnClickListener {
            scanSmbShares()
        }

        binding.btnSmbAddToResources.setOnClickListener {
            // Add selected SMB resources from scan results
            viewModel.addSelectedResources()
        }

        binding.btnSmbAddManually.setOnClickListener {
            // Add manually entered SMB resource
            addSmbResourceManually()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Filter resources by type
                    val localResources = state.resourcesToAdd.filter { 
                        it.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.LOCAL 
                    }
                    val smbResources = state.resourcesToAdd.filter { 
                        it.type == com.sza.fastmediasorter_v2.domain.model.ResourceType.SMB 
                    }
                    
                    // Update adapters
                    resourceToAddAdapter.submitList(localResources)
                    resourceToAddAdapter.setSelectedPaths(state.selectedPaths)
                    
                    smbResourceToAddAdapter.submitList(smbResources)
                    smbResourceToAddAdapter.setSelectedPaths(state.selectedPaths)
                    
                    // Local folder UI visibility
                    val hasLocalResources = localResources.isNotEmpty()
                    binding.tvResourcesToAdd.isVisible = hasLocalResources
                    binding.rvResourcesToAdd.isVisible = hasLocalResources
                    binding.btnAddToResources.isVisible = hasLocalResources
                    
                    // SMB folder UI visibility
                    val hasSmbResources = smbResources.isNotEmpty()
                    binding.tvSmbResourcesToAdd.isVisible = hasSmbResources
                    binding.rvSmbResourcesToAdd.isVisible = hasSmbResources
                    binding.btnSmbAddToResources.isVisible = hasSmbResources
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loading.collect { isLoading ->
                    binding.progressBar.isVisible = isLoading
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AddResourceEvent.ShowError -> {
                            showError(event.message)
                        }
                        is AddResourceEvent.ShowMessage -> {
                            Toast.makeText(this@AddResourceActivity, event.message, Toast.LENGTH_SHORT).show()
                        }
                        is AddResourceEvent.ShowTestResult -> {
                            showTestResultDialog(event.message, event.isSuccess)
                        }
                        AddResourceEvent.ResourcesAdded -> {
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun showTestResultDialog(message: String, isSuccess: Boolean) {
        val title = if (isSuccess) "Connection Test - Success" else "Connection Test - Failed"
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(message)
            }
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Test Result", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            if (settings.showDetailedErrors) {
                AlertDialog.Builder(this@AddResourceActivity)
                    .setTitle("Error")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                Toast.makeText(this@AddResourceActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLocalFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.text = getString(com.sza.fastmediasorter_v2.R.string.add_local_folder)
        binding.layoutLocalFolder.isVisible = true
    }

    private fun showSmbFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.text = getString(com.sza.fastmediasorter_v2.R.string.add_network_folder)
        binding.layoutSmbFolder.isVisible = true
    }

    private fun testSmbConnection() {
        val server = binding.etSmbServer.text.toString().trim()
        val shareName = binding.etSmbShareName.text.toString().trim()
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445

        if (server.isEmpty()) {
            Toast.makeText(this, "Server address is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        // shareName is optional - if empty, tests server and lists shares
        // if provided, tests specific share access
        viewModel.testSmbConnection(server, shareName, username, password, domain, port)
    }

    private fun scanSmbShares() {
        val server = binding.etSmbServer.text.toString().trim()
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445

        if (server.isEmpty()) {
            Toast.makeText(this, "Server address is required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.scanSmbShares(server, username, password, domain, port)
    }

    /**
     * Add manually entered SMB resource (when user types share name directly)
     */
    private fun addSmbResourceManually() {
        val server = binding.etSmbServer.text.toString().trim()
        val shareName = binding.etSmbShareName.text.toString().trim()
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445

        if (server.isEmpty()) {
            Toast.makeText(this, "Server address is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (shareName.isEmpty()) {
            Toast.makeText(this, "Share name is required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addSmbResourceManually(server, shareName, username, password, domain, port)
    }

    /**
     * Setup IP address input field with auto-fill and validation
     * Spec: Auto-fill with device IP subnet (e.g., "192.168.1."), 
     * allow only digits and dots, replace comma with dot,
     * block 4th dot and 4-digit numbers, validate each octet (0-255)
     */
    private fun setupIpAddressField() {
        // Auto-fill with device IP subnet
        val deviceIp = getLocalIpAddress()
        if (deviceIp != null) {
            val subnet = deviceIp.substringBeforeLast(".") + "."
            binding.etSmbServer.setText(subnet)
            binding.etSmbServer.setSelection(subnet.length)
        }

        // IP address input filter: digits, dots, comma→dot, validate octets
        val ipFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val beforeText = dest.toString()
            
            val filtered = StringBuilder()
            var dotCount = beforeText.count { it == '.' }
            
            for (i in start until end) {
                val c = source[i]
                when {
                    c.isDigit() -> {
                        // Check if adding this digit would create 4-digit number
                        val currentOctet = getCurrentOctet(beforeText, dstart) + filtered.toString() + c
                        if (currentOctet.length <= 3) {
                            val octetValue = currentOctet.toIntOrNull()
                            if (octetValue != null && octetValue <= 255) {
                                filtered.append(c)
                            }
                        }
                    }
                    c == '.' || c == ',' -> {
                        // Block 4th dot
                        if (dotCount < 3) {
                            filtered.append('.')
                            dotCount++
                        }
                    }
                    // Skip all other characters
                }
            }
            
            if (filtered.toString() == source.subSequence(start, end).toString()) {
                null // no changes needed
            } else {
                filtered.toString()
            }
        }
        
        binding.etSmbServer.filters = arrayOf(ipFilter)
    }

    /**
     * Get current octet being edited in IP address
     */
    private fun getCurrentOctet(text: String, position: Int): String {
        val beforeCursor = text.substring(0, position)
        val lastDotIndex = beforeCursor.lastIndexOf('.')
        return if (lastDotIndex >= 0) {
            beforeCursor.substring(lastDotIndex + 1)
        } else {
            beforeCursor
        }
    }

    /**
     * Get device's local IP address
     * Returns IP in format "192.168.1.100" or null if not found
     */
    private fun getLocalIpAddress(): String? {
        try {
            // Try WiFi first
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.let { wifiInfo ->
                val ipInt = wifiInfo.ipAddress
                if (ipInt != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                }
            }

            // Try all network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting local IP address")
        }
        return null
    }
}

