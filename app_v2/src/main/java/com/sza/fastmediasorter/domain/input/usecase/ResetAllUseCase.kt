package com.sza.fastmediasorter.domain.input.usecase

import com.sza.fastmediasorter.data.input.InputBindingRepository
import timber.log.Timber
import javax.inject.Inject

class ResetAllUseCase @Inject constructor(private val repo: InputBindingRepository) {

    suspend operator fun invoke() {
        Timber.d("ResetAll: clearing all overrides")
        repo.clearAll()
    }
}
