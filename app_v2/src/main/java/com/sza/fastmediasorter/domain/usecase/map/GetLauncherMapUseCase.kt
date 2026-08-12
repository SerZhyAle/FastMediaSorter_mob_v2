package com.sza.fastmediasorter.domain.usecase.map

import com.sza.fastmediasorter.domain.repository.MapRepository
import com.sza.fastmediasorter.domain.repository.MapResult
import javax.inject.Inject

/** S1175: the map gadget's only door to the map data - it never touches the repository directly. */
class GetLauncherMapUseCase @Inject constructor(
    private val repository: MapRepository,
) {

    suspend operator fun invoke(): MapResult = repository.current()
}
