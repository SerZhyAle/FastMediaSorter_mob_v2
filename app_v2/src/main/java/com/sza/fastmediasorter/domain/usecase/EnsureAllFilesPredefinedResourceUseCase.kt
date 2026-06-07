package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.os.Environment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class EnsureAllFilesPredefinedResourceResult(
    val resourceId: Long,
    val created: Boolean
)

class EnsureAllFilesPredefinedResourceUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val addResourceUseCase: AddResourceUseCase,
    private val resolveResourceIconUseCase: ResolveResourceIconUseCase
) {
    suspend operator fun invoke(): Result<EnsureAllFilesPredefinedResourceResult> = runCatching {
        val rootPath = resolveRootPath()
        val existing = resourceRepository.getAllResourcesSync()
            .firstOrNull { isPredefinedResource(it, rootPath) }

        if (existing != null) {
            return@runCatching EnsureAllFilesPredefinedResourceResult(
                resourceId = existing.id,
                created = false
            )
        }

        val settings = settingsRepository.getSettings().first()
        val rootDir = File(rootPath)
        val resource = MediaResource(
            id = 0,
            name = context.getString(R.string.all_files),
            path = rootPath,
            type = ResourceType.LOCAL,
            supportedMediaTypes = MediaType.entries.toSet(),
            sortMode = SortMode.DATE_DESC,
            displayMode = DisplayMode.LIST,
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            isWritable = rootDir.exists() && rootDir.canWrite(),
            isReadOnly = false,
            scanSubdirectories = true,
            allFiles = true,
            showHiddenFiles = settings.showHiddenFiles,
            showSubfoldersAsItems = settings.showSubfoldersAsItems,
            rememberFileList = false,
            profile = ResourceProfile.ALL_FILES,
            iconId = resolveResourceIconUseCase(
                path = rootPath,
                profile = ResourceProfile.ALL_FILES,
                type = ResourceType.LOCAL
            )
        )

        val createdId = addResourceUseCase(resource, addToTop = true).getOrThrow()
        Timber.i("Created predefined All Files resource: id=%d path=%s", createdId, rootPath)
        EnsureAllFilesPredefinedResourceResult(resourceId = createdId, created = true)
    }

    suspend fun exists(): Boolean {
        val rootPath = resolveRootPath()
        return resourceRepository.getAllResourcesSync().any { isPredefinedResource(it, rootPath) }
    }

    fun isPredefinedResource(resource: MediaResource): Boolean =
        isPredefinedResource(resource, resolveRootPath())

    private fun isPredefinedResource(resource: MediaResource, rootPath: String): Boolean {
        return resource.type == ResourceType.LOCAL &&
            resource.path == rootPath &&
            resource.profile == ResourceProfile.ALL_FILES &&
            resource.allFiles &&
            resource.scanSubdirectories
    }

    private fun resolveRootPath(): String {
        val externalRoot = runCatching { Environment.getExternalStorageDirectory() }
            .getOrNull()
            ?.absolutePath
            ?.takeIf { it.isNotBlank() }
        return externalRoot ?: DEFAULT_ROOT_PATH
    }

    private companion object {
        private const val DEFAULT_ROOT_PATH = "/storage/emulated/0"
    }
}
