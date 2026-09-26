package com.sza.fastmediasorter.ui.wear.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.WearFaceSlot
import com.sza.fastmediasorter.domain.model.WearFaceSlotAssignment
import com.sza.fastmediasorter.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.domain.usecase.ObserveWearFaceSlotsUseCase
import com.sza.fastmediasorter.domain.usecase.SetWearFaceSlotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S3558: the companion window's watch face buttons group.
 *
 * Its own view model rather than more of WearSyncViewModel, which is at detekt's constructor ceiling
 * and owns the settings push - this group never goes through that push, the record is published by
 * PushWearFaceSlotsUseCase on its own.
 */
@HiltViewModel
class WearFaceSlotsViewModel @Inject constructor(
    observeWearFaceSlots: ObserveWearFaceSlotsUseCase,
    private val setWearFaceSlot: SetWearFaceSlotUseCase,
) : ViewModel() {

    val assignment: StateFlow<WearFaceSlotAssignment> = observeWearFaceSlots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WearFaceSlotAssignment.DEFAULT)

    fun select(slot: WearFaceSlot, option: WearFaceSlotOption) {
        viewModelScope.launch { setWearFaceSlot(slot, option) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
