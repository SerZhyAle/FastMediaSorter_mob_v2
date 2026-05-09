package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class GeneralSettingsCredentialHelper(
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val importCredentialsLauncher: ActivityResultLauncher<Array<String>>,
) {
    fun importTestCredentials() {
        try {
            importCredentialsLauncher.launch(arrayOf("application/json", "*/*"))
        } catch (e: Exception) {
            // S0118: friendly copy via resource, no protocol-style error.
            Toast.makeText(fragment.requireContext(), R.string.settings_credentials_picker_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun importCredentialsFromUri(uri: android.net.Uri) {
        fragment.lifecycleScope.launch {
            try {
                Timber.i("Importing test credentials from URI: $uri")
                val json = withContext(Dispatchers.IO) {
                    fragment.requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    }
                }
                if (json.isNullOrBlank()) {
                    Toast.makeText(fragment.requireContext(), R.string.settings_credentials_file_empty, Toast.LENGTH_LONG).show()
                    return@launch
                }

                val jsonObject = org.json.JSONObject(json)
                var credentialsImported = 0
                var resourcesImported = 0
                var settingsImported = false
                val credentialNameMap = mutableMapOf<String, String>()

                if (jsonObject.has("credentials")) {
                    val credArray = jsonObject.getJSONArray("credentials")
                    for (i in 0 until credArray.length()) {
                        val cred = credArray.getJSONObject(i)
                        val name = cred.getString("name")
                        val uuid = java.util.UUID.randomUUID().toString()
                        val credentials = NetworkCredentialsEntity.create(
                            credentialId = uuid,
                            type = cred.getString("type"),
                            server = cred.getString("server"),
                            port = cred.optInt("port", 445),
                            username = cred.optString("username", ""),
                            plaintextPassword = cred.optString("password", ""),
                            domain = cred.optString("domain", ""),
                            shareName = cred.optString("shareName", "")
                        )
                        viewModel.addCredentials(credentials)
                        credentialNameMap[name] = uuid
                        credentialsImported++
                    }
                }

                if (jsonObject.has("resources")) {
                    val resArray = jsonObject.getJSONArray("resources")
                    for (i in 0 until resArray.length()) {
                        val res = resArray.getJSONObject(i)
                        var credentialsId: String? = null
                        if (res.has("credentialsName")) {
                            credentialsId = credentialNameMap[res.getString("credentialsName")]
                        }
                        val mediaTypes = mutableSetOf<MediaType>()
                        if (res.has("supportedMediaTypes")) {
                            val typesArray = res.getJSONArray("supportedMediaTypes")
                            for (j in 0 until typesArray.length()) {
                                mediaTypes.add(MediaType.valueOf(typesArray.getString(j)))
                            }
                        }
                        val resource = MediaResource(
                            id = 0,
                            name = res.getString("name"),
                            path = res.getString("path"),
                            type = ResourceType.valueOf(res.getString("type")),
                            createdDate = System.currentTimeMillis(),
                            fileCount = 0,
                            isDestination = res.optBoolean("isDestination", false),
                            destinationOrder = if (res.optBoolean("isDestination", false)) res.optInt("destinationOrder", 0) else null,
                            credentialsId = credentialsId,
                            isWritable = true,
                            scanSubdirectories = res.optBoolean("scanSubdirectories", true),
                            supportedMediaTypes = mediaTypes,
                            slideshowInterval = 10,
                            allFiles = false,
                            cloudProvider = if (res.has("type") && res.getString("type") == "CLOUD" && res.has("cloudProvider"))
                                com.sza.fastmediasorter.data.cloud.CloudProvider.valueOf(res.getString("cloudProvider"))
                            else null
                        )
                        viewModel.addResourceDirectly(resource)
                        resourcesImported++
                    }
                }

                if (jsonObject.has("settings")) {
                    val settings = jsonObject.getJSONObject("settings")
                    val currentSettings = viewModel.settings.value
                    viewModel.updateSettings(currentSettings.copy(
                        defaultSortMode = if (settings.has("defaultSortMode"))
                            SortMode.valueOf(settings.getString("defaultSortMode"))
                        else currentSettings.defaultSortMode,
                        slideshowInterval = settings.optInt("slideshowInterval", currentSettings.slideshowInterval),
                        networkParallelism = settings.optInt("parallelDownloads", currentSettings.networkParallelism),
                        cacheSizeMb = settings.optInt("cacheSizeMb", currentSettings.cacheSizeMb),
                        supportImages = settings.optBoolean("supportImages", currentSettings.supportImages),
                        supportVideos = settings.optBoolean("supportVideos", currentSettings.supportVideos),
                        supportAudio = settings.optBoolean("supportAudio", currentSettings.supportAudio),
                        supportGifs = settings.optBoolean("supportGifs", currentSettings.supportGifs),
                        supportText = settings.optBoolean("supportText", currentSettings.supportText),
                        supportPdf = settings.optBoolean("supportPdf", currentSettings.supportPdf),
                        imageSizeMin = settings.optLong("imageMinSize", currentSettings.imageSizeMin),
                        imageSizeMax = settings.optLong("imageMaxSize", currentSettings.imageSizeMax),
                        videoSizeMin = settings.optLong("videoMinSize", currentSettings.videoSizeMin),
                        videoSizeMax = settings.optLong("videoMaxSize", currentSettings.videoSizeMax),
                        audioSizeMin = settings.optLong("audioMinSize", currentSettings.audioSizeMin),
                        audioSizeMax = settings.optLong("audioMaxSize", currentSettings.audioSizeMax),
                        showVideoThumbnails = settings.optBoolean("showVideoThumbnails", currentSettings.showVideoThumbnails),
                        showPdfThumbnails = settings.optBoolean("showPdfThumbnails", currentSettings.showPdfThumbnails),
                        loadFullSizeImages = settings.optBoolean("loadFullSizeImages", currentSettings.loadFullSizeImages),
                        preventSleep = settings.optBoolean("preventSleep", currentSettings.preventSleep),
                        showSmallControls = settings.optBoolean("showSmallControls", currentSettings.showSmallControls),
                        defaultGridMode = settings.optBoolean("gridMode", currentSettings.defaultGridMode),
                        defaultIconSize = settings.optInt("iconSize", currentSettings.defaultIconSize),
                        defaultShowCommandPanel = settings.optBoolean("showCommandPanel", currentSettings.defaultShowCommandPanel),
                        showDetailedErrors = settings.optBoolean("detailedErrors", currentSettings.showDetailedErrors),
                        confirmDelete = settings.optBoolean("confirmDelete", currentSettings.confirmDelete),
                        confirmMove = settings.optBoolean("confirmMove", currentSettings.confirmMove),
                        allowRename = settings.optBoolean("allowRename", currentSettings.allowRename),
                        allowDelete = settings.optBoolean("allowDelete", currentSettings.allowDelete),
                        enableCopying = settings.optBoolean("copyingEnabled", currentSettings.enableCopying),
                        enableMoving = settings.optBoolean("movingEnabled", currentSettings.enableMoving),
                        enableUndo = settings.optBoolean("undoEnabled", currentSettings.enableUndo),
                        maxRecipients = settings.optInt("maxRecipients", currentSettings.maxRecipients),
                        goToNextAfterCopy = settings.optBoolean("goToNextAfterCopy", currentSettings.goToNextAfterCopy)
                    ))
                    settingsImported = true
                }

                // S0118: emoji-free, localized success copy.
                val message = if (settingsImported) {
                    fragment.getString(R.string.settings_credentials_import_success_with_settings, credentialsImported, resourcesImported)
                } else {
                    fragment.getString(R.string.settings_credentials_import_success, credentialsImported, resourcesImported)
                }
                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show()
                Timber.i("Import complete: $credentialsImported credentials, $resourcesImported resources, settings=$settingsImported")

            } catch (e: Exception) {
                Timber.e(e, "Failed to import test credentials")
                val reason = e.message ?: fragment.getString(R.string.settings_unknown_error)
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_credentials_import_failed, reason), Toast.LENGTH_LONG).show()
            }
        }
    }
}
