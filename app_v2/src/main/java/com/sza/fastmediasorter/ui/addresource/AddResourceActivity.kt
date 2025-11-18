package com.sza.fastmediasorter.ui.addresource

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.cloud.GoogleDriveClient
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.NetworkInterface
import javax.inject.Inject

@AndroidEntryPoint
class AddResourceActivity : BaseActivity<ActivityAddResourceBinding>() {

    private val viewModel: AddResourceViewModel by viewModels()
    
    private var copyResourceId: Long? = null
    
    @Inject
    lateinit var googleDriveClient: GoogleDriveClient
    
    private lateinit var resourceToAddAdapter: ResourceToAddAdapter
    private lateinit var smbResourceToAddAdapter: ResourceToAddAdapter
    
    private var googleDriveAccount: GoogleSignInAccount? = null

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Timber.d("Selected folder: $uri")
            viewModel.addManualFolder(uri)
        }
    }
    
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleGoogleSignInResult(result.data)
    }
    
    private val sshKeyFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadSshKeyFromFile(it) }
    }

    override fun getViewBinding(): ActivityAddResourceBinding {
        return ActivityAddResourceBinding.inflate(layoutInflater)
    }
    
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if copying existing resource
        copyResourceId = intent.getLongExtra(EXTRA_COPY_RESOURCE_ID, -1L).takeIf { it != -1L }
        
        copyResourceId?.let { resourceId ->
            // Update toolbar title for copy mode
            binding.toolbar.title = getString(R.string.copy_resource_title)
            // Load resource data for pre-filling
            viewModel.loadResourceForCopy(resourceId)
        } ?: run {
            binding.toolbar.title = getString(R.string.add_resource_title)
        }
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
        
        binding.cardSftpFolder.setOnClickListener {
            showSftpFolderOptions()
        }
        
        binding.cardCloudStorage.setOnClickListener {
            showCloudStorageOptions()
        }
        
        binding.cardGoogleDrive.setOnClickListener {
            authenticateGoogleDrive()
        }
        
        // SFTP/FTP protocol RadioGroup
        binding.rgProtocol.setOnCheckedChangeListener { _, checkedId ->
            val currentPort = binding.etSftpPort.text.toString()
            when (checkedId) {
                binding.rbSftp.id -> {
                    // Set port to 22 if empty or if it's FTP port (21)
                    if (currentPort.isBlank() || currentPort == "21") {
                        binding.etSftpPort.setText("22")
                    }
                }
                binding.rbFtp.id -> {
                    // Set port to 21 if empty or if it's SFTP port (22)
                    if (currentPort.isBlank() || currentPort == "22") {
                        binding.etSftpPort.setText("21")
                    }
                }
            }
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
        
        // SFTP buttons
        binding.btnSftpTest.setOnClickListener {
            testSftpConnection()
        }
        
        binding.btnSftpAddResource.setOnClickListener {
            addSftpResource()
        }
        
        // SFTP auth method selection
        binding.rgSftpAuthMethod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbSftpPassword -> {
                    binding.layoutSftpPasswordAuth.isVisible = true
                    binding.layoutSftpSshKeyAuth.isVisible = false
                }
                R.id.rbSftpSshKey -> {
                    binding.layoutSftpPasswordAuth.isVisible = false
                    binding.layoutSftpSshKeyAuth.isVisible = true
                }
            }
        }
        
        // SSH key file picker
        binding.btnSftpLoadKey.setOnClickListener {
            sshKeyFilePickerLauncher.launch(arrayOf("*/*"))
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Filter resources by type
                    val localResources = state.resourcesToAdd.filter { 
                        it.type == com.sza.fastmediasorter.domain.model.ResourceType.LOCAL 
                    }
                    val smbResources = state.resourcesToAdd.filter { 
                        it.type == com.sza.fastmediasorter.domain.model.ResourceType.SMB 
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
                        is AddResourceEvent.LoadResourceForCopy -> {
                            preFillResourceData(event.resource)
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
        binding.tvTitle.text = getString(com.sza.fastmediasorter.R.string.add_local_folder)
        binding.layoutLocalFolder.isVisible = true
    }

    private fun showSmbFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.text = getString(com.sza.fastmediasorter.R.string.add_network_folder)
        binding.layoutSmbFolder.isVisible = true
        binding.layoutSftpFolder.isVisible = false
    }
    
    private fun showSftpFolderOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.text = "Add SFTP/FTP Resource"
        binding.layoutSmbFolder.isVisible = false
        binding.layoutSftpFolder.isVisible = true
        binding.layoutCloudStorage.isVisible = false
        
        // Set default port to 22 (SFTP) when opening this section
        if (binding.etSftpPort.text.isNullOrBlank()) {
            binding.etSftpPort.setText("22")
        }
        
        // Select SFTP by default
        binding.rbSftp.isChecked = true
    }
    
    private fun showCloudStorageOptions() {
        binding.layoutResourceTypes.isVisible = false
        binding.tvTitle.text = getString(R.string.cloud_storage)
        binding.layoutSmbFolder.isVisible = false
        binding.layoutSftpFolder.isVisible = false
        binding.layoutCloudStorage.isVisible = true
        
        updateCloudStorageStatus()
    }
    
    private fun updateCloudStorageStatus() {
        googleDriveAccount = GoogleSignIn.getLastSignedInAccount(this)
        
        if (googleDriveAccount != null) {
            binding.tvGoogleDriveStatus.text = getString(R.string.connected_as, googleDriveAccount?.email ?: "")
            binding.tvGoogleDriveStatus.isVisible = true
        } else {
            binding.tvGoogleDriveStatus.text = getString(R.string.not_connected)
            binding.tvGoogleDriveStatus.isVisible = true
        }
    }
    
    private fun authenticateGoogleDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        
        if (account != null) {
            showGoogleDriveSignedInOptions(account)
        } else {
            launchGoogleSignIn()
        }
    }
    
    private fun launchGoogleSignIn() {
        lifecycleScope.launch {
            try {
                val signInIntent = googleDriveClient.getSignInIntent()
                googleSignInLauncher.launch(signInIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to launch Google Sign-In")
                Toast.makeText(
                    this@AddResourceActivity,
                    getString(R.string.google_drive_authentication_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun handleGoogleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            googleDriveAccount = account
            updateCloudStorageStatus()
            
            Toast.makeText(
                this,
                getString(R.string.google_drive_signed_in, account.email ?: ""),
                Toast.LENGTH_SHORT
            ).show()
            
            // Navigate to folder selection
            navigateToGoogleDriveFolderPicker()
            
        } catch (e: ApiException) {
            Timber.e(e, "Google Sign-In failed: ${e.statusCode}")
            Toast.makeText(
                this,
                getString(R.string.google_drive_authentication_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun navigateToGoogleDriveFolderPicker() {
        val intent = Intent(this, com.sza.fastmediasorter.ui.cloudfolders.GoogleDriveFolderPickerActivity::class.java)
        startActivity(intent)
    }
    
    private fun showGoogleDriveSignedInOptions(account: GoogleSignInAccount) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.google_drive))
            .setMessage(getString(R.string.connected_as, account.email ?: ""))
            .setPositiveButton(R.string.google_drive_select_folder) { _, _ ->
                navigateToGoogleDriveFolderPicker()
            }
            .setNegativeButton(R.string.google_drive_sign_out) { _, _ ->
                signOutGoogleDrive()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun signOutGoogleDrive() {
        GoogleSignIn.getClient(this, googleDriveClient.getSignInOptions()).signOut().addOnCompleteListener {
            googleDriveAccount = null
            updateCloudStorageStatus()
            Toast.makeText(this, "Signed out from Google Drive", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testSmbConnection() {
        val server = binding.etSmbServer.text.toString().trim().replace(',', '.')
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
        val server = binding.etSmbServer.text.toString().trim().replace(',', '.')
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
        val server = binding.etSmbServer.text.toString().trim().replace(',', '.')
        val shareName = binding.etSmbShareName.text.toString().trim()
        val username = binding.etSmbUsername.text.toString().trim()
        val password = binding.etSmbPassword.text.toString().trim()
        val domain = binding.etSmbDomain.text.toString().trim()
        val portStr = binding.etSmbPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 445
        val addToDestinations = binding.cbSmbAddToDestinations.isChecked

        if (server.isEmpty()) {
            Toast.makeText(this, "Server address is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (shareName.isEmpty()) {
            Toast.makeText(this, "Share name is required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addSmbResourceManually(server, shareName, username, password, domain, port, addToDestinations)
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

        // Relaxed input filter: allow digits, dots, comma→dot, letters, dash, underscore
        // This allows IP addresses and hostnames
        val serverFilter = InputFilter { source, start, end, _, _, _ ->
            val filtered = StringBuilder()
            
            for (i in start until end) {
                val c = source[i]
                when {
                    c.isDigit() || c.isLetter() || c == '.' || c == '-' || c == '_' -> {
                        filtered.append(c)
                    }
                    c == ',' -> {
                        // Replace comma with dot
                        filtered.append('.')
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
        
        binding.etSmbServer.filters = arrayOf(serverFilter)
    }

    /**
     * Get device's local IP address
     * Returns IP in format "192.168.1.100" or null if not found
     */
    private fun getLocalIpAddress(): String? {
        try {
            // Try WiFi first
            @Suppress("DEPRECATION")
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo?.let { wifiInfo ->
                @Suppress("DEPRECATION")
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
    
    // ========== SFTP/FTP Methods ==========
    
    private fun getSelectedProtocol(): com.sza.fastmediasorter.domain.model.ResourceType {
        return when (binding.rgProtocol.checkedRadioButtonId) {
            binding.rbSftp.id -> com.sza.fastmediasorter.domain.model.ResourceType.SFTP
            binding.rbFtp.id -> com.sza.fastmediasorter.domain.model.ResourceType.FTP
            else -> com.sza.fastmediasorter.domain.model.ResourceType.SFTP // Default to SFTP
        }
    }
    
    private fun testSftpConnection() {
        val protocolType = getSelectedProtocol()
        val host = binding.etSftpHost.text.toString().trim()
        val portStr = binding.etSftpPort.text.toString().trim()
        val defaultPort = if (protocolType == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) 22 else 21
        val port = portStr.toIntOrNull() ?: defaultPort
        val username = binding.etSftpUsername.text.toString().trim()
        
        if (host.isEmpty()) {
            Toast.makeText(this, "Host is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Determine auth method for SFTP
        if (protocolType == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) {
            val useSshKey = binding.rbSftpSshKey.isChecked
            if (useSshKey) {
                val privateKey = binding.etSftpPrivateKey.text.toString().trim()
                val keyPassphrase = binding.etSftpKeyPassphrase.text.toString().trim().ifEmpty { null }
                
                if (privateKey.isEmpty()) {
                    Toast.makeText(this, "SSH private key is required", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Test with SSH key
                viewModel.testSftpConnectionWithKey(host, port, username, privateKey, keyPassphrase)
            } else {
                // Test with password
                val password = binding.etSftpPassword.text.toString().trim()
                viewModel.testSftpFtpConnection(protocolType, host, port, username, password)
            }
        } else {
            // FTP always uses password
            val password = binding.etSftpPassword.text.toString().trim()
            viewModel.testSftpFtpConnection(protocolType, host, port, username, password)
        }
    }
    
    private fun addSftpResource() {
        val protocolType = getSelectedProtocol()
        val host = binding.etSftpHost.text.toString().trim()
        val portStr = binding.etSftpPort.text.toString().trim()
        val defaultPort = if (protocolType == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) 22 else 21
        val port = portStr.toIntOrNull() ?: defaultPort
        val username = binding.etSftpUsername.text.toString().trim()
        val remotePath = binding.etSftpPath.text.toString().trim().ifEmpty { "/" }
        
        if (host.isEmpty()) {
            Toast.makeText(this, "Host is required", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Determine auth method for SFTP
        if (protocolType == com.sza.fastmediasorter.domain.model.ResourceType.SFTP) {
            val useSshKey = binding.rbSftpSshKey.isChecked
            if (useSshKey) {
                val privateKey = binding.etSftpPrivateKey.text.toString().trim()
                val keyPassphrase = binding.etSftpKeyPassphrase.text.toString().trim().ifEmpty { null }
                
                if (privateKey.isEmpty()) {
                    Toast.makeText(this, "SSH private key is required", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Add with SSH key
                viewModel.addSftpResourceWithKey(host, port, username, privateKey, keyPassphrase, remotePath)
            } else {
                // Add with password
                val password = binding.etSftpPassword.text.toString().trim()
                viewModel.addSftpFtpResource(protocolType, host, port, username, password, remotePath)
            }
        } else {
            // FTP always uses password
            val password = binding.etSftpPassword.text.toString().trim()
            viewModel.addSftpFtpResource(protocolType, host, port, username, password, remotePath)
        }
    }
    
    private fun loadSshKeyFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val keyContent = inputStream.bufferedReader().use { it.readText() }
                binding.etSftpPrivateKey.setText(keyContent)
                Toast.makeText(this, "SSH key loaded successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load SSH key from file")
            Toast.makeText(this, getString(R.string.sftp_key_load_error), Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Pre-fill form fields with data from resource being copied
     */
    private fun preFillResourceData(resource: com.sza.fastmediasorter.domain.model.MediaResource) {
        Timber.d("Pre-filling data from resource: ${resource.name} (type: ${resource.type})")
        
        when (resource.type) {
            com.sza.fastmediasorter.domain.model.ResourceType.LOCAL -> {
                // Show local folder section
                showLocalFolderOptions()
                // For local, path is already selected by user via folder picker
                // We can't pre-select it, but show message
                Toast.makeText(
                    this,
                    "Select folder location for the copy",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            com.sza.fastmediasorter.domain.model.ResourceType.SMB -> {
                // Show SMB section and pre-fill fields
                showSmbFolderOptions()
                
                // Parse SMB path: smb://server/share/path
                val smbPath = resource.path.removePrefix("smb://")
                val parts = smbPath.split("/")
                
                if (parts.isNotEmpty()) {
                    binding.etSmbServer.setText(parts[0])
                }
                if (parts.size > 1) {
                    binding.etSmbShareName.setText(parts[1])
                }
                
                // Note: credentials loaded separately via viewModel
                binding.etSmbPort.setText("445")
                
                Toast.makeText(
                    this,
                    "Review and modify SMB connection details",
                    Toast.LENGTH_SHORT
                ).show()
            }
            
            com.sza.fastmediasorter.domain.model.ResourceType.SFTP -> {
                // Show SFTP section and pre-fill fields
                showSftpFolderOptions()
                
                // Parse SFTP path: sftp://host:port/path
                val sftpPath = resource.path.removePrefix("sftp://")
                val hostAndPath = sftpPath.split("/", limit = 2)
                
                if (hostAndPath.isNotEmpty()) {
                    val hostPort = hostAndPath[0].split(":")
                    binding.etSftpHost.setText(hostPort[0])
                    if (hostPort.size > 1) {
                        binding.etSftpPort.setText(hostPort[1])
                    } else {
                        binding.etSftpPort.setText("22")
                    }
                }
                
                if (hostAndPath.size > 1) {
                    binding.etSftpPath.setText("/" + hostAndPath[1])
                }
                
                binding.rbSftp.isChecked = true
                
                Toast.makeText(
                    this,
                    "Review and modify SFTP connection details",
                    Toast.LENGTH_SHORT
                ).show()
            }
            
            com.sza.fastmediasorter.domain.model.ResourceType.FTP -> {
                // Show FTP section (same UI as SFTP)
                showSftpFolderOptions()
                
                // Parse FTP path: ftp://host:port/path
                val ftpPath = resource.path.removePrefix("ftp://")
                val hostAndPath = ftpPath.split("/", limit = 2)
                
                if (hostAndPath.isNotEmpty()) {
                    val hostPort = hostAndPath[0].split(":")
                    binding.etSftpHost.setText(hostPort[0])
                    if (hostPort.size > 1) {
                        binding.etSftpPort.setText(hostPort[1])
                    } else {
                        binding.etSftpPort.setText("21")
                    }
                }
                
                if (hostAndPath.size > 1) {
                    binding.etSftpPath.setText("/" + hostAndPath[1])
                }
                
                binding.rbFtp.isChecked = true
                
                Toast.makeText(
                    this,
                    "Review and modify FTP connection details",
                    Toast.LENGTH_SHORT
                ).show()
            }
            
            else -> {
                // CLOUD or other future types
                showCloudStorageOptions()
                
                Toast.makeText(
                    this,
                    "Select cloud folder for the copy",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    companion object {
        private const val EXTRA_COPY_RESOURCE_ID = "extra_copy_resource_id"
        
        fun createIntent(context: Context, copyResourceId: Long? = null): Intent {
            return Intent(context, AddResourceActivity::class.java).apply {
                copyResourceId?.let { putExtra(EXTRA_COPY_RESOURCE_ID, it) }
            }
        }
    }
}
