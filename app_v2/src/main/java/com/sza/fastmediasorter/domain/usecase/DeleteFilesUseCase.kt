package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

class DeleteFilesUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val fileOperationUseCase: FileOperationUseCase
) {
    suspend operator fun invoke(files: List<File>): FileOperationResult {
        val settings = settingsRepository.getSettings().first()
        val useTrash = settings.useTrash

        val canUseSoftDeleteForAllFiles = DeletePathPolicy.canUseSoftDelete(
            files.map { it.absolutePath }
        )
        val effectiveSoftDelete = useTrash && canUseSoftDeleteForAllFiles
        
        return fileOperationUseCase.execute(
            FileOperation.Delete(files, softDelete = effectiveSoftDelete)
        )
    }
}
