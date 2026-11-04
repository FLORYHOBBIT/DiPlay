package com.shilapi.xcertplay.aion

import android.content.Context
import android.os.SystemClock
import android.util.Log

/** Measures a real Bluetooth-triggered attempt; no timer or startup dependency is introduced. */
object AionStartupTrace {
    private var requestedAt: Long? = null
    @Synchronized fun begin(context: Context): Long {
        val now = SystemClock.elapsedRealtime()
        requestedAt = now
        val edit = BluetoothPolicyService.preferences(context).edit().putLong("startup_requested_elapsed", now)
        for (stage in listOf("host", "controller", "hotspot", "bluetooth", "first_frame")) edit.remove("startup_${stage}_ms")
        edit.apply()
        return now
    }
    @Synchronized fun cancel(token: Long) {
        if (requestedAt == token) requestedAt = null
    }
    @Synchronized fun stage(context: Context, name: String): Long? {
        val start = requestedAt ?: return null
        val elapsed = SystemClock.elapsedRealtime() - start
        if (elapsed < 0) { requestedAt = null; return null }
        val prefs = BluetoothPolicyService.preferences(context)
        if (!prefs.contains("startup_${name}_ms")) prefs.edit().putLong("startup_${name}_ms", elapsed).apply()
        Log.i("AionStartup", "stage=$name elapsedMs=$elapsed")
        return elapsed
    }
    @Synchronized fun firstFrame(context: Context): Long? {
        val elapsed = stage(context, "first_frame") ?: return null
        requestedAt = null
        BluetoothPolicyService.preferences(context).edit().putString(BluetoothPolicyService.LAUNCH_STATUS,
            "蓝牙触发到 CarPlay 首帧：${elapsed / 1000}.${elapsed % 1000 / 100} 秒（本次实测）").apply()
        return elapsed
    }
}
