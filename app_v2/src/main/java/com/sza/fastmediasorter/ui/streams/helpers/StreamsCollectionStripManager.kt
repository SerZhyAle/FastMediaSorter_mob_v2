package com.sza.fastmediasorter.ui.streams.helpers

import android.view.View
import androidx.core.os.ConfigurationCompat
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamCollectionsUseCase.StreamCollection
import java.util.Locale

/**
 * S2669: owns the curated-collection chip strip - the only place that knows how a collection reaches
 * the screen. The Activity hands it state and receives a selected id back; it holds no logic itself.
 *
 * The strip is `GONE` while nothing was delivered, including the leading "all" chip: an archive without
 * collections has to leave this screen exactly as it was, and an empty strip would still add height.
 *
 * Selection is never signalled by colour alone - each chip is checkable and shows its check icon when
 * selected - and a selection change announces the resulting channel count on the list header, which is
 * the part of the screen the change actually alters.
 */
class StreamsCollectionStripManager(
    private val strip: View,
    private val chipGroup: ChipGroup,
    private val announcementTarget: View,
    private val onCollectionSelected: (String?) -> Unit,
) {

    private var renderedIds: List<String> = emptyList()
    private var appliedSelection: String? = null
    private var selectionEverApplied = false

    /**
     * [matchedCount] is the size of the list the current filter produced. It is passed in rather than
     * derived here because this manager does not see the catalog - it only reports what changed.
     */
    fun render(collections: List<StreamCollection>, selectedId: String?, matchedCount: Int) {
        val ids = collections.map { it.id }
        if (ids != renderedIds) {
            rebuildChips(collections)
            renderedIds = ids
        }
        strip.isVisible = collections.isNotEmpty()
        applySelection(selectedId, matchedCount)
    }

    private fun rebuildChips(collections: List<StreamCollection>) {
        chipGroup.removeAllViews()
        if (collections.isEmpty()) return
        val locale = ConfigurationCompat.getLocales(chipGroup.resources.configuration)
            .get(0) ?: Locale.getDefault()
        chipGroup.addView(newChip(chipGroup.context.getString(R.string.streams_collection_all), null))
        collections.forEach { collection ->
            val name = StreamCollectionNameResolver.resolve(
                collectionId = collection.id,
                namesJson = collection.namesJson,
                locale = locale,
            )
            chipGroup.addView(newChip(name, collection.id))
        }
        // A rebuilt group has no checked chip; force the next applySelection to paint one.
        selectionEverApplied = false
    }

    private fun newChip(label: String, collectionId: String?): Chip {
        val chip = Chip(chipGroup.context)
        // The Chip(Context) constructor resolves the theme's plain chip style, which is not checkable.
        // Material's documented way to give a programmatic chip the filter look is the drawable.
        chip.setChipDrawable(
            ChipDrawable.createFromAttributes(
                chipGroup.context,
                null,
                0,
                com.google.android.material.R.style.Widget_Material3_Chip_Filter,
            )
        )
        chip.text = label
        chip.tag = collectionId
        chip.isCheckable = true
        chip.isCheckedIconVisible = true
        chip.isFocusable = true
        chip.isClickable = true
        chip.setOnClickListener { onCollectionSelected(collectionId) }
        return chip
    }

    // announceForAccessibility is deprecated in favour of live regions, which announce a VISIBLE text
    // change. Nothing visible changes on the header here - the count is spoken only - so a live region
    // would either say nothing or force the count into the header's label for every sighted user too.
    @Suppress("DEPRECATION")
    private fun applySelection(selectedId: String?, matchedCount: Int) {
        val changed = selectedId != appliedSelection
        chipGroup.children().forEach { chip -> chip.isChecked = chip.tag == selectedId }
        if (changed && selectionEverApplied) {
            announcementTarget.announceForAccessibility(
                announcementTarget.context.getString(R.string.streams_collection_selected_count, matchedCount)
            )
        }
        appliedSelection = selectedId
        selectionEverApplied = true
    }

    private fun ChipGroup.children(): List<Chip> =
        (0 until childCount).mapNotNull { index -> getChildAt(index) as? Chip }
}
