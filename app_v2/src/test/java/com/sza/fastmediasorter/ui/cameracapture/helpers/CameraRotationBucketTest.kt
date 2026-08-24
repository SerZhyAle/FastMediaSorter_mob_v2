package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1986: proves the angle-to-bucket mapping on the JVM.
 *
 * It has to be proven here and nowhere else: the device sweep that measures the capture pipeline
 * drives the bucket through a debug hook, because a host cannot turn a real phone, so no on-device
 * run ever exercises this function. Every quadrant edge is checked from both sides - an off-by-one
 * there rotates every photo taken while the phone rests near that angle.
 */
class CameraRotationBucketTest {

    @Test
    fun `natural portrait maps to rotation 0`() {
        assertEquals(Surface.ROTATION_0, CameraRotationBucket.bucketFor(0))
        assertEquals(Surface.ROTATION_0, CameraRotationBucket.bucketFor(44))
        assertEquals(Surface.ROTATION_0, CameraRotationBucket.bucketFor(315))
        assertEquals(Surface.ROTATION_0, CameraRotationBucket.bucketFor(359))
    }

    @Test
    fun `device turned clockwise maps to the inverse surface rotation`() {
        // The listener reports the device's clockwise angle; the buffer needs the opposite turn, so
        // 90 degrees of device is ROTATION_270. Naming it the other way round is the classic mistake.
        assertEquals(Surface.ROTATION_270, CameraRotationBucket.bucketFor(45))
        assertEquals(Surface.ROTATION_270, CameraRotationBucket.bucketFor(90))
        assertEquals(Surface.ROTATION_270, CameraRotationBucket.bucketFor(134))
    }

    @Test
    fun `upside down portrait maps to rotation 180`() {
        assertEquals(Surface.ROTATION_180, CameraRotationBucket.bucketFor(135))
        assertEquals(Surface.ROTATION_180, CameraRotationBucket.bucketFor(180))
        assertEquals(Surface.ROTATION_180, CameraRotationBucket.bucketFor(224))
    }

    @Test
    fun `device turned counter clockwise maps to rotation 90`() {
        assertEquals(Surface.ROTATION_90, CameraRotationBucket.bucketFor(225))
        assertEquals(Surface.ROTATION_90, CameraRotationBucket.bucketFor(270))
        assertEquals(Surface.ROTATION_90, CameraRotationBucket.bucketFor(314))
    }

    @Test
    fun `overlay counter rotation is the negative of the device angle`() {
        assertEquals(0f, CameraRotationBucket.iconDegreesFor(Surface.ROTATION_0), 0f)
        assertEquals(90f, CameraRotationBucket.iconDegreesFor(Surface.ROTATION_90), 0f)
        assertEquals(180f, CameraRotationBucket.iconDegreesFor(Surface.ROTATION_180), 0f)
        // Not 270: a portrait-locked overlay turns the short way, and 270 would spin it the long way
        // round through an animation the user sees.
        assertEquals(-90f, CameraRotationBucket.iconDegreesFor(Surface.ROTATION_270), 0f)
    }
}
