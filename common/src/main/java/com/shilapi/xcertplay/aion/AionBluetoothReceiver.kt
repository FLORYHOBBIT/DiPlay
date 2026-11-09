package com.shilapi.xcertplay.aion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.shilapi.xcertplay.AionIntegration

/** The protected ACL broadcast is the entry point, including when the application process is dead. */
class AionBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AionIntegration.enabled(context) || !AionIntegration.supported()) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                val prefs = BluetoothPolicyService.preferences(context)
                if (!device.address.equals(prefs.getString(BluetoothPolicyService.ADDRESS, null), ignoreCase = true)) return
                // Media policy must run even when CarPlay auto-start is off or this ACL is a duplicate.
                resume(context)
                if (!prefs.getBoolean(BluetoothPolicyService.AUTO_DIPLAY, false)) return
                val now = SystemClock.elapsedRealtime()
                // elapsedRealtime restarts at boot. A value saved on the previous drive must not
                // suppress the first ACL event of this boot; deduplicate only in this process.
                val last = lastLaunch
                if (last != null && last.app === context.applicationContext && last.address.equals(device.address, ignoreCase = true) &&
                    now >= last.elapsed && now - last.elapsed < 15_000) return
                if (AionIntegration.startCarPlay(context, device.address)) {
                    lastLaunch = Launch(context.applicationContext, device.address, now)
                    prefs.edit().putLong("direct_start_elapsed", now)
                        .putString(BluetoothPolicyService.LAUNCH_STATUS, "蓝牙连接已直接请求启动 CarPlay，显示结果待核验").apply()
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            BluetoothAdapter.ACTION_STATE_CHANGED -> resume(context)
        }
    }

    companion object {
        private data class Launch(val app: Context, val address: String, val elapsed: Long)
        @Volatile private var lastLaunch: Launch? = null
        @JvmStatic fun resume(context: Context) {
            if (!AionIntegration.enabled(context) || !AionIntegration.supported()) return
            runCatching { BluetoothPolicyService.startIfEnabled(context) }.onFailure {
                BluetoothPolicyService.preferences(context).edit().putString(BluetoothPolicyService.STATUS,
                    "蓝牙媒体策略未启动：${it.javaClass.simpleName}").apply()
            }
        }
    }
}
