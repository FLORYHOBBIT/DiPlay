package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.os.Build
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/** The mobile AION edition opts in explicitly; shared/sample builds keep their original behaviour. */
object AionIntegration {
    @JvmStatic fun enabled(context: Context): Boolean = context.resources.getBoolean(R.bool.aion_edition)

    @JvmStatic fun supported(): Boolean = com.shilapi.xcertplay.aion.AionDevicePolicy.target(
        Build.VERSION.SDK_INT, Build.MODEL, Build.DISPLAY)

    @JvmStatic fun savePhone(context: Context, address: String, name: String) {
        DiPlayPreferences.savePhone(context, address, name)
        DiPlayPreferences.saveConnectOnPhoneBluetooth(context, true)
    }

    fun phoneSaved(context: Context, address: String, name: String) {
        if (!enabled(context)) return
        val prefs = context.getSharedPreferences("bluetooth-phone-only", Context.MODE_PRIVATE)
        val edit = prefs.edit().putString("address", address).putString("name", name)
        if (prefs.getString("address", null) != address) edit.remove("direct_start_elapsed")
        edit.apply()
    }

    @JvmStatic fun setMapEnabled(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveClusterMapEnabled(context, enabled)
        context.getSharedPreferences("aion-edition", Context.MODE_PRIVATE).edit()
            .putBoolean("reconnect_needed", true).apply()
    }

    fun consumeReconnectNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences("aion-edition", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("reconnect_needed", false)) return false
        prefs.edit().putBoolean("reconnect_needed", false).apply()
        return true
    }

    @JvmStatic fun startCarPlay(context: Context, address: String): Boolean {
        if (!enabled(context) || !supported() || CarPlayBackgroundSession.hasSession() || CarPlayScreen.current() != null) return false
        if (!address.equals(DiPlayPreferences.phoneAddress(context), ignoreCase = true)) return false
        AirPlayPersistence.saveWirelessEnabled(context, true)
        val attempt = com.shilapi.xcertplay.aion.AionStartupTrace.begin(context)
        return runCatching {
            context.startActivity(CarPlayScreen.intent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }.onFailure {
            com.shilapi.xcertplay.aion.AionStartupTrace.cancel(attempt)
            android.util.Log.w("AionBluetoothStart", "Direct CarPlay start failed", it)
        }.isSuccess
    }

    /** Only a new edition install gets defaults. Subsequent launches preserve the driver's choices. */
    fun initialize(context: Context) {
        if (!enabled(context)) return
        com.shilapi.xcertplay.setup.SetupGuide.markSeen(context)
        val prefs = context.getSharedPreferences("aion-edition", Context.MODE_PRIVATE)
        if (prefs.getBoolean("initialized", false)) return
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveWifiDirectScanPause(context, false)
        prefs.edit().putBoolean("initialized", true).apply()
    }

    internal fun categories(context: Context) = SettingsCategory.entries.filter {
        !enabled(context) || it !in setOf(SettingsCategory.LANGUAGE, SettingsCategory.ADVANCED)
    }

    internal fun section(context: Context, section: SettingsSection): Boolean = !enabled(context) || section in setOf(
        SettingsSection.CONNECTION_SETUP, SettingsSection.AUTOMATIC_CONNECTION,
        SettingsSection.USB_CONNECTION, SettingsSection.DISPLAY_AND_PERFORMANCE,
        SettingsSection.AUDIO_ROUTING, SettingsSection.LOCATION,
        SettingsSection.CARPLAY_CONTROLS, SettingsSection.CAR_BUTTON,
        SettingsSection.DIAGNOSTICS, SettingsSection.PERMISSIONS_AND_HELP,
        SettingsSection.AION_BLUETOOTH, SettingsSection.AION_MUSIC, SettingsSection.AION_MAP,
    )
}
