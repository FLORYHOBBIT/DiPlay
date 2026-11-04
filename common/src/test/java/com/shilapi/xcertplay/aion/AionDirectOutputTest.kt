package com.shilapi.xcertplay.aion

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.shilapi.xcertplay.AionIntegration
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.CarPlayScreen
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class AionDirectOutputTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun matchingCar() {
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "G6SA-r8a7796")
        ReflectionHelpers.setStaticField(Build::class.java, "DISPLAY", "GAC_A29_AVNT_02_31.59_220908_R")
    }
    private fun edition(test: () -> Unit) {
        matchingCar()
        Mockito.mockStatic(AionIntegration::class.java, Answer { call ->
            if (call.method.name == "enabled") true else call.callRealMethod()
        }).use { test() }
    }

    @Test fun coldBluetoothReceiverStartsTheCarPlayHostWithoutAnAudioPolicyService() = edition {
        val address = "00:11:22:33:44:55"
        DiPlayPreferences.savePhone(context, address, "iPhone")
        BluetoothPolicyService.preferences(context).edit().putString(BluetoothPolicyService.ADDRESS, address)
            .putBoolean(BluetoothPolicyService.AUTO_DIPLAY, true).putBoolean(BluetoothPolicyService.ENABLED, false)
            // Simulate a previous boot's persisted counter coinciding with this boot's uptime.
            .putLong("direct_start_elapsed", android.os.SystemClock.elapsedRealtime()).commit()
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
        AionBluetoothReceiver().onReceive(context, Intent(BluetoothDevice.ACTION_ACL_CONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, device))
        val started = shadowOf(context).nextStartedActivity
        assertNotNull(started)
        assertEquals(CarPlayHostActivity::class.java.name, started.component?.className)
        assertNull("No assistant/policy service is required to launch", shadowOf(context).nextStartedService)
        AionBluetoothReceiver().onReceive(context, Intent(BluetoothDevice.ACTION_ACL_CONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, device))
        assertNull("Duplicate ACL events must not launch twice", shadowOf(context).nextStartedActivity)
    }

    @Test fun anUnselectedPhoneCannotStartCarPlay() = edition {
        DiPlayPreferences.savePhone(context, "00:11:22:33:44:55", "iPhone")
        BluetoothPolicyService.preferences(context).edit().putString(BluetoothPolicyService.ADDRESS, "00:11:22:33:44:55")
            .putBoolean(BluetoothPolicyService.AUTO_DIPLAY, true).commit()
        val other = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:66")
        AionBluetoothReceiver().onReceive(context, Intent(BluetoothDevice.ACTION_ACL_CONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, other))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test fun internalSongDataUsesAReadableContentUriAndKeepsArtworkStableForLyrics() = edition {
        val vendor = PackageInfo().apply {
            packageName = AionDevicePolicy.MUSIC_PACKAGE; versionName = "V_20220814_1359_CONFOUND"
            applicationInfo = ApplicationInfo().apply { packageName = AionDevicePolicy.MUSIC_PACKAGE; uid = 1000 }
        }
        shadowOf(context.packageManager).installPackage(vendor)
        AionInstrumentMusic.prefs(context).edit().putBoolean(AionInstrumentMusic.ENABLED, true).commit()
        // Use a real encoded PNG: the legacy Android Bitmap shadow's compress output is synthetic.
        val fixture = java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 32) for (x in 0 until 32) fixture.setRGB(x, y, 0xffff0000.toInt())
        val bytes = ByteArrayOutputStream().also { check(javax.imageio.ImageIO.write(fixture, "png", it)) }.toByteArray()
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertEquals("PNG fixture width", 32, bounds.outWidth)
        assertEquals("PNG fixture height", 32, bounds.outHeight)
        val owner = Any()
        try {
            AionInstrumentMusic.publish(context, owner, CarPlayNowPlaying(title = "歌曲 · 歌词一", artworkTransferId = 7), bytes, true)
            drainMusic()
            val broadcasts = shadowOf(context).broadcastIntents
            assertTrue(AionInstrumentMusic.prefs(context).getString(AionInstrumentMusic.STATUS, "no output"), broadcasts.any { it.getIntExtra("cluster_cmd", 0) == 104 })
            val cover = broadcasts.single { it.getIntExtra("cluster_cmd", 0) == 104 }.getBundleExtra("cluster_bundle")!!.getString("albumPath")!!
            val uri = android.net.Uri.parse(cover)
            assertEquals("content", uri.scheme)
            assertTrue(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.isNotEmpty())
            org.junit.Assert.assertThrows(SecurityException::class.java) { context.contentResolver.openFileDescriptor(uri, "w") }
            org.junit.Assert.assertThrows(SecurityException::class.java) {
                InstrumentArtworkProvider.uri(context, java.io.File(context.noBackupFilesDir, "${"a".repeat(64)}.png"))
            }
            assertTrue(broadcasts.any { it.getBundleExtra("cluster_bundle")?.getString("mediaName") == "歌曲 · 歌词一" })
            AionInstrumentMusic.publish(context, owner, CarPlayNowPlaying(title = "歌曲 · 歌词二", artworkTransferId = 7), bytes, true)
            drainMusic()
            assertEquals("Lyric updates must not resend the same cover", 1, shadowOf(context).broadcastIntents.count { it.getIntExtra("cluster_cmd", 0) == 104 })
            assertNull(shadowOf(context).nextStartedService)
        } finally { AionInstrumentMusic.clear(context, owner); drainMusic() }
    }

    @Test fun mapSurfaceIsOwnedByTheMatchedPhysicalDisplayAndDetachesOnClose() {
        matchingCar()
        val manager = context.getSystemService(DisplayManager::class.java)
        val id = ShadowDisplayManager.addDisplay("w1920dp-h720dp-mdpi")
        val display = manager.getDisplay(id)
        shadowOf(display).setName("HDMI 屏幕")
        shadowOf(display).setRealWidth(1920); shadowOf(display).setRealHeight(720)
        assertEquals(1, id)
        val before = manager.displays.map { it.displayId }
        val surfaces = mutableListOf<Surface?>()
        val output = AionInstrumentMap(context, { surfaces += it }, {})
        val texture = SurfaceTexture(0)
        try {
            assertTrue(output.show())
            val window = ReflectionHelpers.getField<android.app.Presentation>(output, "presentation")
            val root = window.findViewById<ViewGroup>(android.R.id.content)
            val view = root.getChildAt(0) as TextureView
            view.surfaceTextureListener!!.onSurfaceTextureAvailable(texture, 1920, 720)
            assertSame(output.outputSurface, surfaces.last())
            assertEquals("No intermediate virtual display", before, manager.displays.map { it.displayId })
            assertFalse(output.rendered)
            view.surfaceTextureListener!!.onSurfaceTextureUpdated(texture)
            assertTrue(output.rendered)
            output.close()
            assertNull(surfaces.last())
            assertNull(output.outputSurface)
            assertFalse(output.rendered)
            val stale = view.surfaceTextureListener!!
            stale.onSurfaceTextureAvailable(texture, 1920, 720)
            stale.onSurfaceTextureUpdated(texture)
            assertNull(output.outputSurface)
            assertFalse(output.rendered)
            assertTrue(output.show())
            val next = ReflectionHelpers.getField<android.app.Presentation>(output, "presentation")
                .findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as TextureView
            next.surfaceTextureListener!!.onSurfaceTextureAvailable(texture, 1920, 720)
            val current = output.outputSurface
            stale.onSurfaceTextureDestroyed(texture)
            assertSame("An old window cannot detach a replacement window", current, output.outputSurface)
            assertSame(current, surfaces.last())
        } finally { output.close(); texture.release(); ShadowDisplayManager.removeDisplay(id) }
    }

    private fun drainMusic() {
        ReflectionHelpers.getStaticField<ExecutorService>(AionInstrumentMusic::class.java, "worker")
            .submit {}.get(10, TimeUnit.SECONDS)
    }
}
