package com.sza.fastmediasorter.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.LayoutDefaults.MultiButtonLayoutDefaults.MAX_BUTTONS
import androidx.wear.protolayout.material.layouts.MultiButtonLayout
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearLaunchExtra
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.extras
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

private const val MAX_FAVOURITES_PREVIEW_ENTRIES = 3

/**
 * S1955: Builds ProtoLayout element trees for Wear OS tiles based on [WearTileContent].
 *
 * Checks `deviceParameters.rendererSchemaVersion` to avoid drawing elements that require a higher
 * schema version than the watch's renderer supports.
 */
class WearTileLayoutBuilder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun build(
        content: WearTileContent,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.Layout {
        val rootElement = when (content) {
            is WearTileContent.Assigned -> buildAssignedLayout(content)
            is WearTileContent.Unassigned -> buildUnassignedLayout(
                kind = content.kind,
                deviceParameters = deviceParameters
            )
            is WearTileContent.TargetMissing -> buildTargetMissingLayout(
                kind = content.kind,
                deviceParameters = deviceParameters
            )
            WearTileContent.FavouritesEmpty -> buildFavouritesEmptyLayout()
            is WearTileContent.Shortcuts -> buildShortcutsLayout(content)
        }

        return LayoutElementBuilders.Layout.Builder()
            .setRoot(rootElement)
            .build()
    }

    private fun buildAssignedLayout(
        content: WearTileContent.Assigned
    ): LayoutElementBuilders.LayoutElement {
        val columnBuilder = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(context, content.title)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setMaxLines(2)
                    .build()
            )

        content.subtitle?.let { sub ->
            columnBuilder.addContent(
                Text.Builder(context, sub)
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setMaxLines(1)
                    .build()
            )
        }

        if (content.entries.isNotEmpty()) {
            content.entries.take(MAX_FAVOURITES_PREVIEW_ENTRIES).forEach { entry ->
                columnBuilder.addContent(
                    Text.Builder(context, entry)
                        .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                        .setMaxLines(1)
                        .build()
                )
            }
        }

        val launchAction = buildLaunchAction(content.launchTarget)

        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setOnClick(launchAction)
                    .setId("open_target")
                    .build()
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .addContent(columnBuilder.build())
            .setModifiers(modifiers)
            .setHeight(DimensionBuilders.expand())
            .setWidth(DimensionBuilders.expand())
            .build()
    }

    /**
     * S2511: a grid of icon buttons, one per shortcut.
     *
     * The list is cut to [MAX_BUTTONS] here rather than trusted to be short enough. `MultiButtonLayout`
     * throws above that count instead of truncating, and an exception inside a tile request hands the
     * system an error tile in place of content - while the two catalogs feeding this grid are documented as
     * growing by a single line, by authors who have no reason to know a tile reads them.
     */
    private fun buildShortcutsLayout(
        content: WearTileContent.Shortcuts
    ): LayoutElementBuilders.LayoutElement {
        val shown = content.entries.take(MAX_BUTTONS)
        Timber.d("S2511: shortcut grid drawing %d button(s)", shown.size)
        if (content.entries.size > shown.size) {
            Timber.w(
                "Shortcut tile holds %d entries, %d fit - dropped the last %d",
                content.entries.size,
                shown.size,
                content.entries.size - shown.size
            )
        }

        val layoutBuilder = MultiButtonLayout.Builder()
        shown.forEach { shortcut ->
            val clickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(buildLaunchAction(shortcut.launchTarget))
                .setId(shortcut.launchTarget.clickId())
                .build()
            layoutBuilder.addButtonContent(
                Button.Builder(context, clickable)
                    .setIconContent(tileImageResourceId(shortcut.iconResId))
                    .setContentDescription(shortcut.contentDescription)
                    .build()
            )
        }

        return LayoutElementBuilders.Box.Builder()
            .addContent(layoutBuilder.build())
            .setHeight(DimensionBuilders.expand())
            .setWidth(DimensionBuilders.expand())
            .build()
    }

    private fun buildUnassignedLayout(
        kind: WearTileKind,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val labelRes = when (kind) {
            WearTileKind.RESOURCE -> R.string.wear_tile_unassigned_resource
            WearTileKind.STREAM -> R.string.wear_tile_unassigned_stream
            // Unreachable for the two grids - they have no assignment, so they never reach this state
            // (S2511). Named rather than sent to an else so a future kind must still be classified.
            WearTileKind.FAVOURITES,
            WearTileKind.PROGRAMS,
            WearTileKind.SECTIONS -> R.string.wear_tile_favourites_empty
        }
        val labelText = context.getString(labelRes)
        val pickText = context.getString(R.string.wear_tile_pick_action)

        val launchTarget = WearLaunchTarget.Pick(kind)
        val launchAction = buildLaunchAction(launchTarget)

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(launchAction)
            .setId("pick_target")
            .build()

        val columnBuilder = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(context, labelText)
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setMaxLines(2)
                    .build()
            )
            .addContent(
                CompactChip.Builder(
                    context,
                    pickText,
                    clickable,
                    deviceParameters
                ).build()
            )

        return LayoutElementBuilders.Box.Builder()
            .addContent(columnBuilder.build())
            .setHeight(DimensionBuilders.expand())
            .setWidth(DimensionBuilders.expand())
            .build()
    }

    private fun buildTargetMissingLayout(
        kind: WearTileKind,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val missingText = context.getString(R.string.wear_tile_target_missing)
        val pickText = context.getString(R.string.wear_tile_pick_action)

        val launchTarget = WearLaunchTarget.Pick(kind)
        val launchAction = buildLaunchAction(launchTarget)

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(launchAction)
            .setId("repick_target")
            .build()

        val columnBuilder = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(context, missingText)
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setMaxLines(2)
                    .build()
            )
            .addContent(
                CompactChip.Builder(
                    context,
                    pickText,
                    clickable,
                    deviceParameters
                ).build()
            )

        return LayoutElementBuilders.Box.Builder()
            .addContent(columnBuilder.build())
            .setHeight(DimensionBuilders.expand())
            .setWidth(DimensionBuilders.expand())
            .build()
    }

    private fun buildFavouritesEmptyLayout(): LayoutElementBuilders.LayoutElement {
        val emptyText = context.getString(R.string.wear_tile_favourites_empty)

        val columnBuilder = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(context, emptyText)
                    .setTypography(Typography.TYPOGRAPHY_BODY1)
                    .setMaxLines(2)
                    .build()
            )

        return LayoutElementBuilders.Box.Builder()
            .addContent(columnBuilder.build())
            .setHeight(DimensionBuilders.expand())
            .setWidth(DimensionBuilders.expand())
            .build()
    }

    /**
     * S2511: every extra crosses, not only the string ones.
     *
     * This mapping is the tile's whole transport, and it used to be built by writing the target into a
     * throwaway `Intent` and reading the values back out untyped - which answered `String` for some fields
     * and `Any?` for the rest, so everything that was not a `String` was dropped. A resource address carries
     * a numeric port, so the port never arrived, the completeness test in `readWearLaunchTarget` failed, and
     * the tap read as a plain launch: the resource tile opened the home screen instead of the pinned
     * resource. The round-trip test did not catch it because it exercises a real `Intent`, where an `Int`
     * passes; only this ProtoLayout hop dropped it. Reading the declared shape leaves no unrecognised case.
     */
    private fun buildLaunchAction(target: WearLaunchTarget): ActionBuilders.LaunchAction {
        val activityBuilder = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName("com.sza.fastmediasorter.wear.MainActivity")

        val extras = target.extras()
        Timber.d("S2511: tile launch action carries %d extra(s)", extras.size)
        extras.forEach { (key, extra) ->
            val value = when (extra) {
                is WearLaunchExtra.Text -> ActionBuilders.stringExtra(extra.value)
                is WearLaunchExtra.Number -> ActionBuilders.intExtra(extra.value)
            }
            activityBuilder.addKeyToExtraMapping(key, value)
        }

        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(activityBuilder.build())
            .build()
    }
}
