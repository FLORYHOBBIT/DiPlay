package com.shilapi.xcertplay.aion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class AionLiteStartupTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearPendingAttemptFromAnotherTestApplication() {
        // Robolectric reuses process statics while replacing Application and preferences between cases.
        AionStartupTrace.cancel(AionStartupTrace.begin(context))
    }

    @Test fun discoveryIsCancelledBeforeTheActualRfcommConnect() {
        val resources = Mockito.mock(Resources::class.java)
        Mockito.`when`(resources.getBoolean(Mockito.anyInt())).thenReturn(false)
        val aion = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getResources(): Resources = resources
        }
        assertFalse(VendorFeatures.enabled(aion))
        val adapter = BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        assertTrue(adapter.startDiscovery())
        assertTrue(adapter.isDiscovering)
        val socket = Mockito.mock(BluetoothSocket::class.java)
        Mockito.doAnswer { assertFalse("System discovery must stop before RFCOMM", adapter.isDiscovering); null }
            .`when`(socket).connect()
        val controller = CarPlayController(aion,
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL,
                identification = Iap2IdentificationConfig(name = "test", modelIdentifier = "test", manufacturer = "test",
                    serialNumber = "test", firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3)),
            AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480)),
            AirPlayIdentity.generate(), PairingStore(), object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, {})
        try {
            controller.javaClass.getDeclaredMethod("connectBluetoothSocket", BluetoothSocket::class.java, String::class.java)
                .apply { isAccessible = true }.invoke(controller, socket, "00:11:22:33:44:55")
            Mockito.verify(socket).connect()
        } finally {
            controller.close(); assertTrue(controller.awaitClosed(2_000))
        }
    }

    @Test fun startupMeasurementRequiresATriggerAndEndsAtTheFirstFrame() {
        assertNull(AionStartupTrace.firstFrame(context))
        AionStartupTrace.begin(context)
        ShadowSystemClock.advanceBy(Duration.ofMillis(120))
        assertEquals(120L, AionStartupTrace.stage(context, "host"))
        ShadowSystemClock.advanceBy(Duration.ofMillis(1230))
        assertEquals(1350L, AionStartupTrace.firstFrame(context))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        assertNull(AionStartupTrace.firstFrame(context))
        assertEquals(1350L, BluetoothPolicyService.preferences(context).getLong("startup_first_frame_ms", -1))
    }

    @Test fun aFailedLaunchCannotProduceASuccessfulTiming() {
        val token = AionStartupTrace.begin(context)
        AionStartupTrace.cancel(token)
        assertNull(AionStartupTrace.firstFrame(context))
        assertFalse(BluetoothPolicyService.preferences(context).contains("startup_first_frame_ms"))
    }
}
