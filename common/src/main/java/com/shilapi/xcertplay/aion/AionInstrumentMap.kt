package com.shilapi.xcertplay.aion

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import com.shilapi.xcertplay.AionIntegration

/** The stream-111 decoder writes to this physical display's Surface, without an intermediate display. */
internal class AionInstrumentMap(
    private val context: Context,
    private val surfaceChanged: (Surface?) -> Unit,
    private val visibilityChanged: () -> Unit,
) {
    private var presentation: Presentation? = null
    var outputSurface: Surface? = null
        private set
    var rendered = false
        private set
    val showing: Boolean get() = presentation?.isShowing == true

    fun show(): Boolean {
        val display = findDisplay(context) ?: return false
        if (showing) return true
        val window = Presentation(context, display)
        window.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setFormat(PixelFormat.TRANSLUCENT)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
        val view = TextureView(window.context).apply {
            // Before the first decoded buffer the existing car screen remains visible underneath.
            isOpaque = false
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                private var ownedSurface: Surface? = null
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    if (presentation !== window) return
                    texture.setDefaultBufferSize(AionDevicePolicy.WIDTH, AionDevicePolicy.HEIGHT)
                    ownedSurface = Surface(texture)
                    outputSurface = ownedSurface
                    surfaceChanged(ownedSurface)
                }
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                    if (presentation !== window || !showing || ownedSurface == null || outputSurface !== ownedSurface) return
                    if (!rendered) {
                        rendered = true
                        AionMapSettings.prefs(context).edit().putBoolean("projecting", true)
                            .putString("status", "CarPlay 地图正在直接输出到仪表").apply()
                        visibilityChanged()
                    }
                }
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    if (ownedSurface != null && outputSurface === ownedSurface) {
                        surfaceChanged(null)
                        outputSurface = null
                        rendered = false
                        visibilityChanged()
                    }
                    ownedSurface?.release(); ownedSurface = null
                    return true
                }
            }
        }
        window.setContentView(view)
        window.setOnDismissListener {
            if (presentation === window) {
                presentation = null
                surfaceChanged(null)
                outputSurface?.release(); outputSurface = null
                rendered = false
                AionMapSettings.prefs(context).edit().putBoolean("projecting", false).apply()
                visibilityChanged()
            }
        }
        presentation = window
        try {
            window.show()
            AionMapSettings.prefs(context).edit().putString("status", "仪表直出已连接，等待地图首帧").apply()
            return true
        } catch (error: RuntimeException) {
            close()
            AionMapSettings.prefs(context).edit().putString("status", "仪表直出未完成：${error.javaClass.simpleName}").apply()
            return false
        }
    }

    fun close() {
        val old = presentation
        presentation = null
        // Detach the decoder while its Surface still exists.
        surfaceChanged(null)
        old?.dismiss()
        outputSurface?.release(); outputSurface = null
        rendered = false
        AionMapSettings.prefs(context).edit().putBoolean("projecting", false)
            .putString("status", "CarPlay 地图已停止，原车画面已恢复").apply()
        visibilityChanged()
    }

    companion object {
        fun findDisplay(context: Context): Display? {
            if (!AionIntegration.supported()) return null
            val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(1) ?: return null
            val size = Point(); display.getRealSize(size)
            return display.takeIf { it.isValid && AionDevicePolicy.instrument(it.displayId, it.name, size.x, size.y) }
        }
    }
}
