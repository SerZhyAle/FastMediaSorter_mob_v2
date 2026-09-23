package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.domain.usecase.ExportFavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.ImportFavoritesUseCase
import javax.inject.Inject

/**
 * S3398: the favorites file round trip - export to a file and import it back - as one collaborator.
 *
 * Grouped because [BackupRestoreViewModel] had reached detekt's constructor ceiling, and the shape the
 * repo already uses for this is a named dependency holder (`WearOutboundUseCases`), not a suppression.
 */
class FavoritesTransferUseCases @Inject constructor(
    val exportFavorites: ExportFavoritesUseCase,
    val importFavorites: ImportFavoritesUseCase
)
