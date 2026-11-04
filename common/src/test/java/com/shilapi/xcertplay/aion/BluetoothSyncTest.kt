package com.shilapi.xcertplay.aion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.Build
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class BluetoothSyncTest {
    class Profile : BluetoothProfile {
        var priority = 1000
        var connected = true
        fun getPriority(device: BluetoothDevice) = priority
        fun setPriority(device: BluetoothDevice, value: Int): Boolean { priority=value; return true }
        fun connect(device: BluetoothDevice): Boolean { connected=true; return true }
        fun disconnect(device: BluetoothDevice): Boolean { connected=false; return true }
        override fun getConnectedDevices(): List<BluetoothDevice> = emptyList()
        override fun getDevicesMatchingConnectionStates(states: IntArray): List<BluetoothDevice> = emptyList()
        override fun getConnectionState(device: BluetoothDevice) = if(connected) 2 else 0
    }

    @Test fun manualMediaSyncPersistsAndRestoresWithoutTouchingCalls() {
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "G6SA-r8a7796")
        ReflectionHelpers.setStaticField(Build::class.java, "DISPLAY", "GAC_A29_AVNT_02_31.59_220908_R")
        val context = RuntimeEnvironment.getApplication()
        val profiles = BluetoothProfiles(context) { }
        val media = Profile(); val phone = Profile()
        ReflectionHelpers.setField(profiles, "media", media)
        ReflectionHelpers.setField(profiles, "phone", phone)
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55")
        assertTrue(profiles.setConnection(false, false, device))
        assertEquals(0, media.priority)
        assertFalse(media.connected)
        assertEquals(1000, phone.priority)
        assertTrue(phone.connected)
        assertTrue(profiles.setConnection(false, true, device))
        assertEquals(100, media.priority)
        profiles.restoreSync(false, device)
        assertEquals(1000, media.priority)
        assertTrue(media.connected)
        assertEquals(1000, phone.priority)
        assertTrue(phone.connected)
    }
}
