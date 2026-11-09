package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ScrollView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.MfiTarget
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h720dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AionHotspotUiTest {
    @Test fun compactHotspotControlIsVisible() = capture("hotspot-compact")
    @Test @Config(qualifiers = "zh-rCN-w1280dp-h720dp-xhdpi")
    fun fullHotspotControlIsVisible() = capture("hotspot-full")
    @Test @Config(qualifiers = "ar-ldrtl-w360dp-h720dp-xhdpi")
    fun arabicSystemHotspotControlIsVisible() = capture("hotspot-ar-system")

    private fun capture(name: String) {
        RuntimeEnvironment.setFontScale(1.3f)
        Mockito.mockStatic(AionIntegration::class.java, Answer { call ->
            if (call.method.name == "enabled") true else call.callRealMethod()
        }).use {
            AirPlayPersistence.saveMfiTarget(RuntimeEnvironment.getApplication(), MfiTarget.USB_CH341)
            val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup().visible()
            try {
                val activity = controller.get()
                ReflectionHelpers.setField(activity, "page", "settings")
                ReflectionHelpers.setField(activity, "settingsCategory", SettingsCategory.CONNECTION)
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                val config = activity.resources.configuration
                val density = activity.resources.displayMetrics.density
                val width = (config.screenWidthDp * density).toInt()
                val height = (config.screenHeightDp * density).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                val matches = views(root).filterIsInstance<TextView>().filter { it.text.toString() == activity.getString(R.string.auto_car_hotspot_title) }.toList()
                assertEquals("AION Connection must expose one hotspot switch", 1, matches.size)
                val target = matches.single()
                val scroll = generateSequence(target.parent) { it.parent }.filterIsInstance<ScrollView>().first()
                val rect = Rect(0, 0, target.width, target.height)
                scroll.offsetDescendantRectToMyCoords(target, rect)
                scroll.scrollTo(0, rect.top.coerceAtLeast(0))
                val card = generateSequence(target as View?) { it.parent as? View }.first { candidate ->
                    candidate is ViewGroup && views(candidate).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.automatic_connection) }
                }
                // At compact widths the complete card scrolls beyond one viewport; export it whole.
                val capture = if (config.screenWidthDp < 600) card else root
                val bitmap = Bitmap.createBitmap(capture.width, capture.height, Bitmap.Config.ARGB_8888)
                try {
                    capture.draw(Canvas(bitmap))
                    val file = File("build/reports/aion-ui/$name.png")
                    file.parentFile!!.mkdirs()
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
            } finally { controller.pause().stop().destroy() }
        }
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
}
