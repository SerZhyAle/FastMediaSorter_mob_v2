package com.sza.fastmediasorter.worker

import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataSyncForegroundTest {

    private val dataSync = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    private val mediaPlayback = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK

    @Test
    fun `declared type with permission on API 34 resolves to dataSync`() {
        val declared = dataSync or mediaPlayback
        val type = DataSyncForeground.resolveType(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, declared, true)
        assertEquals(dataSync, type)
    }

    @Test
    fun `store manifest without dataSync resolves to null`() {
        assertNull(DataSyncForeground.resolveType(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, mediaPlayback, true))
        assertNull(DataSyncForeground.resolveType(Build.VERSION_CODES.Q, mediaPlayback, true))
        assertNull(DataSyncForeground.resolveType(Build.VERSION_CODES.Q, 0, true))
    }

    @Test
    fun `missing permission blocks dataSync only from API 34`() {
        assertNull(DataSyncForeground.resolveType(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, dataSync, false))
        assertEquals(dataSync, DataSyncForeground.resolveType(Build.VERSION_CODES.TIRAMISU, dataSync, false))
    }

    @Test
    fun `below API 29 no type is ever passed`() {
        assertNull(DataSyncForeground.resolveType(Build.VERSION_CODES.P, dataSync, true))
    }
}
