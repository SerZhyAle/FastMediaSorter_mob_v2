package com.sza.fastmediasorter.vr.ui

import com.sza.fastmediasorter.vr.render.VrInteractivePanelComposer

/**
 * Maps a [VrRayPanelHitTester.HitResult] UV coordinate to a panel zone ID and,
 * when the ray is over the seek slider, to a fractional seek position.
 */
class VrPanelHitZoneResolver(private val composer: VrInteractivePanelComposer) {

    /**
     * Returns the zone ID for the given [hit], or -1 on miss / between zones.
     */
    fun resolve(hit: VrRayPanelHitTester.HitResult): Int {
        if (hit.isMiss) return -1
        return composer.zoneAt(hit.u, hit.v)?.id ?: -1
    }

    /**
     * Returns the fractional seek position (0f–1f) when [hit] is over
     * [VrInteractivePanelComposer.ZONE_SEEK_SLIDER], otherwise -1f.
     */
    fun resolveSeekFraction(hit: VrRayPanelHitTester.HitResult): Float {
        if (resolve(hit) != VrInteractivePanelComposer.ZONE_SEEK_SLIDER) return -1f
        val zone = composer.zoneAt(hit.u, hit.v) ?: return -1f
        val zoneWidth = zone.bounds.right - zone.bounds.left
        if (zoneWidth <= 0f) return 0f
        val pixelX = hit.u * composer.width
        return ((pixelX - zone.bounds.left) / zoneWidth).coerceIn(0f, 1f)
    }
}
