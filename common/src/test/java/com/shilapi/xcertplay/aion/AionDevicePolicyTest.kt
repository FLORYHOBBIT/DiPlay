package com.shilapi.xcertplay.aion

import org.junit.Assert.*
import org.junit.Test

class AionDevicePolicyTest {
    @Test fun onlyTheRecordedPhysicalMapRegionIsAccepted() {
        assertTrue(AionDevicePolicy.instrument(1, "HDMI 屏幕", 1920, 720))
        assertFalse(AionDevicePolicy.instrument(0, "HDMI 屏幕", 1920, 720))
        assertFalse(AionDevicePolicy.instrument(2, "HDMI 屏幕", 1920, 720))
        assertFalse(AionDevicePolicy.instrument(1, "virtual", 1920, 720))
        assertFalse(AionDevicePolicy.instrument(1, "HDMI 屏幕", 1280, 720))
    }
    @Test fun leavingTheSessionOrStreamAlwaysRestoresTheCarScreen() {
        for (enabled in listOf(false, true)) for (paused in listOf(false, true))
            for (session in listOf(false, true)) for (stream in listOf(false, true)) {
                assertEquals(enabled && !paused && session && stream, AionDevicePolicy.project(enabled, paused, session, stream))
            }
    }
}
