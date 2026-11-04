package com.shilapi.xcertplay.aion

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import com.shilapi.xcertplay.AionIntegration
import com.shilapi.xcertplay.AirPlayPersistence
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], qualifiers = "zh-rCN-w360dp-h720dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AionPanelsTest {
    @Test fun mapChoiceMarksReconnectAndCanBeConsumedOnlyOnce() {
        val context = RuntimeEnvironment.getApplication()
        AionIntegration.setMapEnabled(context, true)
        assertTrue(AirPlayPersistence.loadClusterMapEnabled(context))
        assertTrue(AionIntegration.consumeReconnectNeeded(context))
        assertFalse(AionIntegration.consumeReconnectNeeded(context))
        AionIntegration.setMapEnabled(context, false)
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(context))
        assertTrue(AionIntegration.consumeReconnectNeeded(context))
    }

    @Test fun unknownFirmwareCannotEnableVendorProjection() {
        val activity = Robolectric.buildActivity(InstrumentMapActivity::class.java).setup().get()
        try {
            val toggle = views(activity.window.decorView).filterIsInstance<CheckBox>().single()
            assertFalse(toggle.isEnabled)
            assertFalse(AionMapSettings.prefs(activity).getBoolean(AionMapSettings.AUTO_ENABLED, false))
        } finally { activity.finish() }
    }

    @Test fun instrumentRequiresExactCarFirmware() {
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "G6SA-r8a7796")
        ReflectionHelpers.setStaticField(Build::class.java, "DISPLAY", "GAC_A29_AVNT_02_31.59_220908_R")
        assertTrue(AionIntegration.supported())
        ReflectionHelpers.setStaticField(Build::class.java, "DISPLAY", "different-firmware")
        assertFalse(AionIntegration.supported())
    }

    @Test @Config(sdk = [35]) fun compactLargeFontScreenshot() = capture("compact-zh")
    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w1280dp-h720dp-xhdpi")
    fun fullLargeFontScreenshot() = capture("full-zh")
    @Test @Config(sdk = [35], qualifiers = "ar-ldrtl-w360dp-h720dp-xhdpi")
    fun arabicSystemLargeFontScreenshot() = capture("compact-ar-system")

    @Test @Config(sdk = [35])
    fun compactBluetoothScreenshot() = capture("bluetooth-compact", BluetoothMediaActivity::class.java)

    @Test @Config(sdk = [35], qualifiers = "zh-rCN-w1280dp-h720dp-xhdpi")
    fun fullBluetoothScreenshot() = capture("bluetooth-full", BluetoothMediaActivity::class.java)

    @Test @Config(sdk = [35])
    fun permissionsIncludeTheAppItselfAndFitCompactWindows() {
        Mockito.mockStatic(AionIntegration::class.java, Answer { invocation ->
            if (invocation.method.name == "supported") true else invocation.callRealMethod()
        }).use {
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            try {
                val activity = controller.get()
                val selected = ReflectionHelpers.getField<Any>(activity, "selected")
                assertEquals(activity.packageName, ReflectionHelpers.getField<String>(selected, "pkg"))
            } finally { controller.pause().stop().destroy() }
            capturePanel("permissions-compact", MainActivity::class.java)
        }
    }

    private fun capture(name: String, type: Class<out Activity> = InstrumentMapActivity::class.java) {
        Mockito.mockStatic(AionIntegration::class.java, Answer { invocation ->
            if (invocation.method.name == "supported") true else invocation.callRealMethod()
        }).use { capturePanel(name, type) }
    }

    private fun capturePanel(name: String, type: Class<out Activity>) {
        RuntimeEnvironment.setFontScale(1.3f)
        val controller = Robolectric.buildActivity(type).setup().visible()
        val activity = controller.get()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val metrics = activity.resources.displayMetrics
            val config = activity.resources.configuration
            val width = (config.screenWidthDp * metrics.density).toInt()
            val height = (config.screenHeightDp * metrics.density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            views(root).filterIsInstance<TextView>().forEach { text ->
                val layout = text.layout ?: return@forEach
                for (line in 0 until layout.lineCount) assertEquals("Truncated: ${text.text}", 0, layout.getEllipsisCount(line))
                assertTrue("Text wider than window: ${text.text}", text.width <= width)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                root.draw(Canvas(bitmap))
                val file = File("build/reports/aion-ui/$name.png"); file.parentFile!!.mkdirs()
                file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        } finally { controller.pause().stop().destroy() }
    }

    private fun views(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) yieldAll(views(root.getChildAt(i)))
    }
}
