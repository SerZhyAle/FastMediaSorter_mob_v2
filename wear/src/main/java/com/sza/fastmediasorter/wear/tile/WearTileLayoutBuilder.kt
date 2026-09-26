package com.sza.fastmediasorter.wear.tile

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.ButtonDefaults
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.MultiButtonLayout
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearLaunchExtra
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileRunningProgram
import com.sza.fastmediasorter.wear.domain.model.WearTileShortcut
import com.sza.fastmediasorter.wear.domain.model.extras
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1955: Builds ProtoLayout element trees for Wear OS tiles based on [WearTileContent].
 *
 * S3555: nothing drawn here needs more than the base renderer schema, so no element is gated on
 * `deviceParameters.rendererSchemaVersion` - a sentence here once claimed a check that no code performed.
 *
 * S2589: every decision this used to take on its own now comes from `WearTileLayoutPlan`; what is left here
 * is the drawing, which no JVM test can reach. The dispatch below stays an exhaustive `when` over the sealed
 * content type with no `else`, so a sixth state fails compilation rather than drawing nothing.
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
            is WearTileContent.Shortcuts -> buildShortcutsLayout(content, deviceParameters)
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
                    .setColor(SURFACE_TEXT)
                    .setMaxLines(2)
                    .build()
            )

        content.subtitle?.let { sub ->
            columnBuilder.addContent(
                Text.Builder(context, sub)
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(SURFACE_TEXT)
                    .setMaxLines(1)
                    .build()
            )
        }

        planAssignedPreview(content.entries).forEach { entry ->
            columnBuilder.addContent(
                Text.Builder(context, entry)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(SURFACE_TEXT)
                    .setMaxLines(1)
                    .build()
            )
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
     * S2511: a grid of icon buttons, one per shortcut, cut to what the grid holds by `planShortcutGrid`.
     *
     * S3555: while a program runs, the grid shares a `PrimaryLayout` with a label saying what runs and a
     * chip that opens it (WO-V4). What runs is the label, not the chip text: a compact chip leaves about
     * 80 dp for text on the 192 dp review watch, which cuts "Stopwatch running" off, while the label line
     * above the grid is wide enough for it. Neither carries a time nor a control - the tile guidance asks
     * for a glanceable way back, redrawn only when the program starts or stops.
     */
    private fun buildShortcutsLayout(
        content: WearTileContent.Shortcuts,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val plan = planShortcutGrid(
            content.entries,
            overflow = overflowShortcut(context),
            capacity = shortcutGridCapacity(content)
        )
        if (plan.dropped > 0) {
            Timber.w(
                "Shortcut tile holds %d entries, %d cells - the last %d are behind the overflow cell",
                content.entries.size,
                plan.shown.size,
                plan.dropped
            )
        }

        val grid = buildShortcutGrid(plan.shown)
        val running = content.running
        return if (running == null) {
            LayoutElementBuilders.Box.Builder()
                .addContent(grid)
                .setHeight(DimensionBuilders.expand())
                .setWidth(DimensionBuilders.expand())
                .build()
        } else {
            buildRunningLayout(grid, running, deviceParameters)
        }
    }

    private fun buildRunningLayout(
        grid: LayoutElementBuilders.LayoutElement,
        running: WearTileRunningProgram,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(buildLaunchAction(WearLaunchTarget.Destination(running.destinationId)))
            .setId(RUNNING_CLICK_ID)
            .build()

        // Two lines rather than an ellipsis: at the largest system font the label wraps, and the one row
        // of buttons below it still fits the round glass on the smallest review watch. The chip's own
        // colour ties the label to the chip that opens what it names.
        val label = Text.Builder(context, running.label)
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(ColorBuilders.argb(Colors.DEFAULT.primary))
            .setMaxLines(2)
            .build()
        val chip = CompactChip.Builder(
            context,
            context.getString(R.string.wear_tile_programs_running_open),
            clickable,
            deviceParameters
        )
            .setContentDescription(running.contentDescription)
            .build()
        return PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(label)
            .setContent(grid)
            .setPrimaryChipContent(chip)
            .build()
    }

    private fun buildShortcutGrid(cells: List<WearTileShortcut>): LayoutElementBuilders.LayoutElement {
        val layoutBuilder = MultiButtonLayout.Builder()
        cells.forEach { shortcut ->
            val clickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(buildLaunchAction(shortcut.launchTarget))
                .setId(shortcut.launchTarget.clickId())
                .build()
            // S3434: the decorated look - the glyph on a plate in its entity's hue, sized by the plate.
            val plate = ContextCompat.getColor(context, tilePlateHueFor(shortcut.destinationId))
            layoutBuilder.addButtonContent(
                Button.Builder(context, clickable)
                    .setButtonColors(ButtonColors(plate, onPlateColorFor(plate)))
                    .setIconContent(
                        tileImageResourceId(tileShortcutIconFor(shortcut.destinationId)),
                        DimensionBuilders.dp(ButtonDefaults.DEFAULT_SIZE.value * TILE_PLATE_GLYPH_RATIO)
                    )
                    .setContentDescription(shortcut.contentDescription)
                    .build()
            )
        }
        // Unwrapped on purpose: `PrimaryLayout` wraps its content slot around the content and cannot take
        // an expanding height, so the plain grid gets its expanding box from the caller instead.
        return layoutBuilder.build()
    }

    private fun buildUnassignedLayout(
        kind: WearTileKind,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val labelText = context.getString(unassignedLabelRes(kind))
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
                    .setColor(SURFACE_TEXT)
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
                    .setColor(SURFACE_TEXT)
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
                    .setColor(SURFACE_TEXT)
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

    private companion object {
        const val RUNNING_CLICK_ID = "open_running"

        /**
         * S3555: text drawn straight on the tile's black background. Material's `Text` defaults to the
         * colour meant for text ON a primary-coloured chip - near-black, which the tile showed as unreadable
         * grey on black until measured on the emulator.
         */
        val SURFACE_TEXT: ColorBuilders.ColorProp = ColorBuilders.argb(Colors.DEFAULT.onSurface)
    }
}
