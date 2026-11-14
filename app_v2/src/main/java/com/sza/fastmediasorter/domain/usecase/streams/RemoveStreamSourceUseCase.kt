package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore
import javax.inject.Inject

class RemoveStreamSourceUseCase @Inject constructor(
    private val repository: StreamSourceRepository,
    private val passwords: BroadcastPasswordStore
) {
    /**
     * The password of a protected broadcast lives in the platform secret store beside the row, not in it,
     * so deleting the row alone would leave a secret that no screen can reach and nothing ever clears.
     */
    suspend operator fun invoke(source: StreamSourceEntity) {
        repository.remove(source)
        passwords.remove(source.id)
    }
}
