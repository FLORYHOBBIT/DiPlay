package com.shilapi.xcertplay.aion

import android.content.Context
import com.shilapi.xcertplay.AionIntegration

/** Preferences remain compatible with the first test APK; map lifetime belongs to the CarPlay host. */
object AionMapSettings {
    const val AUTO_ENABLED = "automatic"
    @JvmStatic fun prefs(context: Context) = context.getSharedPreferences("aion-map-bridge", Context.MODE_PRIVATE)
    var onChanged: (() -> Unit)? = null
    @JvmStatic fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AUTO_ENABLED, enabled).putBoolean("paused", false).apply()
        AionIntegration.setMapEnabled(context, enabled)
        // Stream capability changes are negotiated at the driver's next reconnect.
    }
    @JvmStatic fun pause(context: Context, paused: Boolean) {
        prefs(context).edit().putBoolean("paused", paused).apply()
        onChanged?.invoke()
    }
}
