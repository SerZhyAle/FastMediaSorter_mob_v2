package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.ContextualRationaleRepository
import javax.inject.Inject

class MarkContextualShownUseCase @Inject constructor(
    private val repo: ContextualRationaleRepository
) {
    fun invoke(permissionId: String) = repo.markShown(permissionId)

    fun isShown(permissionId: String) = repo.isShown(permissionId)
}
