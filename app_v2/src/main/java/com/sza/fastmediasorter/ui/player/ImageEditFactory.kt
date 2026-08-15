package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.usecase.AdjustImageUseCase
import com.sza.fastmediasorter.domain.usecase.ApplyImageFilterUseCase
import com.sza.fastmediasorter.domain.usecase.ChangeGifSpeedUseCase
import com.sza.fastmediasorter.domain.usecase.ExtractGifFramesUseCase
import com.sza.fastmediasorter.domain.usecase.FlipImageUseCase
import com.sza.fastmediasorter.domain.usecase.MergeDrawOverlayUseCase
import com.sza.fastmediasorter.domain.usecase.NetworkImageEditUseCase
import com.sza.fastmediasorter.domain.usecase.RotateImageUseCase
import com.sza.fastmediasorter.domain.usecase.SaveGifFirstFrameUseCase
import javax.inject.Inject

/**
 * S1637: owns the image and GIF edit use cases that `PlayerActivity` and `PhotoVideoStandaloneActivity`
 * used to field-inject, so neither host declares a domain dependency of its own.
 *
 * A supplier of objects rather than a behavioural facade: all fifteen call sites measured in strategic
 * §6.1 hand the use case to a constructor and none invokes it, so a facade would carry a surface nothing
 * calls. Receivers (`PlayerDialogHelper`, `ImageEditDialog`, `StandaloneDrawSaveHelper`) keep taking the
 * use case by constructor exactly as written today - no manager signature changes.
 *
 * Unscoped on purpose, mirroring `StandaloneHostFactory`: the use cases themselves carry their own
 * scoping, and the factory must not outlive the host that asked for it.
 */
class ImageEditFactory @Inject constructor(
    val rotateImage: RotateImageUseCase,
    val flipImage: FlipImageUseCase,
    val networkImageEdit: NetworkImageEditUseCase,
    val applyImageFilter: ApplyImageFilterUseCase,
    val adjustImage: AdjustImageUseCase,
    val mergeDrawOverlay: MergeDrawOverlayUseCase,
    val extractGifFrames: ExtractGifFramesUseCase,
    val saveGifFirstFrame: SaveGifFirstFrameUseCase,
    val changeGifSpeed: ChangeGifSpeedUseCase,
)
