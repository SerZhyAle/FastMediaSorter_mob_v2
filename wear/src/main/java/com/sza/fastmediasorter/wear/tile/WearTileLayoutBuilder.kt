package com.sza.fastmediasorter.wear.tile

import android.content.Context
import android.content.Intent
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.model.writeTo
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private fun buildUnassignedLayout(
        kind: WearTileKind,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val labelRes = when (kind) {
            WearTileKind.RESOURCE -> R.string.wear_tile_unassigned_resource
            WearTileKind.STREAM -> R.string.wear_tile_unassigned_stream
            WearTileKind.FAVOURITES -> R.string.wear_tile_favourites_empty
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

    private fun buildLaunchAction(target: WearLaunchTarget): ActionBuilders.LaunchAction {
        val dummyIntent = Intent().also { target.writeTo(it) }
        val activityBuilder = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName("com.sza.fastmediasorter.wear.MainActivity")

        dummyIntent.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                if (value is String) {
                    activityBuilder.addKeyToExtraMapping(
                        key,
                        ActionBuilders.stringExtra(value)
                    )
                }
            }
        }

        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(activityBuilder.build())
            .build()
    }
}
