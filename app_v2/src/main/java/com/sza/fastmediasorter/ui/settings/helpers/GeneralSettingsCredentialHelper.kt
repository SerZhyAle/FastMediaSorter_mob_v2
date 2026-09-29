package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
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
            Toast.makeText(
                fragment.requireContext(),
                R.string.settings_credentials_picker_failed,
                Toast.LENGTH_SHORT
            ).show()
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
                    Toast.makeText(
                        fragment.requireContext(),
                        R.string.settings_credentials_file_empty,
                        Toast.LENGTH_LONG
                    ).show()
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
                            destinationOrder = if (res.optBoolean("isDestination", false)) {
                                res.optInt("destinationOrder", 0)
                            } else {
                                null
                            },
                            credentialsId = credentialsId,
                            isWritable = true,
                            scanSubdirectories = res.optBoolean("scanSubdirectories", true),
                            supportedMediaTypes = mediaTypes,
                            slideshowInterval = 10,
                            allFiles = false,
                            cloudProvider = if (res.has("type") && res.getString("type") == "CLOUD" && res.has("cloudProvider")) {
                                com.sza.fastmediasorter.data.cloud.CloudProvider.valueOf(res.getString("cloudProvider"))
                            } else {
                                null
                            }
                        )
                        viewModel.addResourceDirectly(resource)
                        resourcesImported++
                    }
                }

                if (jsonObject.has("settings")) {
                    val settings = jsonObject.getJSONObject("settings")
                    viewModel.updateSettings {
                        it.copy(
                            defaultSortMode = if (settings.has("defaultSortMode")) {
                                SortMode.valueOf(settings.getString("defaultSortMode"))
                            } else {
                                it.defaultSortMode
                            },
                            slideshowInterval = settings.optInt("slideshowInterval", it.slideshowInterval),
                            networkParallelism = settings.optInt("parallelDownloads", it.networkParallelism),
                            cacheSizeMb = settings.optInt("cacheSizeMb", it.cacheSizeMb),
                            supportImages = settings.optBoolean("supportImages", it.supportImages),
                            supportVideos = settings.optBoolean("supportVideos", it.supportVideos),
                            supportAudio = settings.optBoolean("supportAudio", it.supportAudio),
                            supportGifs = settings.optBoolean("supportGifs", it.supportGifs),
                            supportText = settings.optBoolean("supportText", it.supportText),
                            supportPdf = settings.optBoolean("supportPdf", it.supportPdf),
                            supportEpub = settings.optBoolean("supportEpub", it.supportEpub),
                            supportOfficeDocuments = settings.optBoolean(
                                "supportOfficeDocuments",
                                it.supportOfficeDocuments
                            ),
                            imageSizeMin = settings.optLong("imageMinSize", it.imageSizeMin),
                            imageSizeMax = settings.optLong("imageMaxSize", it.imageSizeMax),
                            videoSizeMin = settings.optLong("videoMinSize", it.videoSizeMin),
                            videoSizeMax = settings.optLong("videoMaxSize", it.videoSizeMax),
                            audioSizeMin = settings.optLong("audioMinSize", it.audioSizeMin),
                            audioSizeMax = settings.optLong("audioMaxSize", it.audioSizeMax),
                            showVideoThumbnails = settings.optBoolean("showVideoThumbnails", it.showVideoThumbnails),
                            showPdfThumbnails = settings.optBoolean("showPdfThumbnails", it.showPdfThumbnails),
                            loadFullSizeImages = settings.optBoolean("loadFullSizeImages", it.loadFullSizeImages),
                            preventSleep = settings.optBoolean("preventSleep", it.preventSleep),
                            keepScreenOnPlayer = settings.optBoolean("keepScreenOnPlayer", it.keepScreenOnPlayer),
                            showSmallControls = settings.optBoolean("showSmallControls", it.showSmallControls),
                            defaultGridMode = settings.optBoolean("gridMode", it.defaultGridMode),
                            defaultIconSize = settings.optInt("iconSize", it.defaultIconSize),
                            defaultShowCommandPanel = settings.optBoolean(
                                "showCommandPanel",
                                it.defaultShowCommandPanel
                            ),
                            showDetailedErrors = settings.optBoolean("detailedErrors", it.showDetailedErrors),
                            confirmDelete = settings.optBoolean("confirmDelete", it.confirmDelete),
                            confirmMove = settings.optBoolean("confirmMove", it.confirmMove),
                            allowRename = settings.optBoolean("allowRename", it.allowRename),
                            allowDelete = settings.optBoolean("allowDelete", it.allowDelete),
                            enableCopying = settings.optBoolean("copyingEnabled", it.enableCopying),
                            enableMoving = settings.optBoolean("movingEnabled", it.enableMoving),
                            enableUndo = settings.optBoolean("undoEnabled", it.enableUndo),
                            maxRecipients = settings.optInt("maxRecipients", it.maxRecipients),
                            goToNextAfterCopy = settings.optBoolean("goToNextAfterCopy", it.goToNextAfterCopy)
                        )
                    }
                    settingsImported = true
                }

                // S0118: emoji-free, localized success copy.
                val message = if (settingsImported) {
                    fragment.getString(
                        R.string.settings_credentials_import_success_with_settings,
                        credentialsImported,
                        resourcesImported
                    )
                } else {
                    fragment.getString(
                        R.string.settings_credentials_import_success,
                        credentialsImported,
                        resourcesImported
                    )
                }
                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show()
                Timber.i(
                    "Import complete: $credentialsImported credentials, $resourcesImported resources, " +
                        "settings=$settingsImported"
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Failed to import test credentials")
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.settings_credentials_import_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
