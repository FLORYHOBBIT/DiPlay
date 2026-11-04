package com.shilapi.xcertplay.aion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.media.session.PlaybackState
import com.shilapi.xcertplay.AionIntegration
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Consumes the controller's retained iAP2 data directly. No MediaController or forwarding service. */
object AionInstrumentMusic {
    const val ENABLED = "enabled"
    const val STATUS = "status"
    @JvmStatic fun prefs(context: Context) = context.getSharedPreferences("aion-song-bridge", Context.MODE_PRIVATE)
    private data class Update(val context: Context, val owner: Any, val info: CarPlayNowPlaying, val cover: ByteArray?)
    @Volatile private var latest: Update? = null
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "AionInstrumentMusic").apply { isDaemon = true } }
    private var sentOwner: Any? = null
    private var lastTitle = ""
    private var lastState = -1
    private var lastCover: Uri? = null
    private var preparedBytes: ByteArray? = null
    private var preparedUri: Uri? = null
    private var preparedFile: File? = null
    private var artworkDiagnostic: String? = null

    @JvmStatic @Synchronized fun publish(context: Context, owner: Any, info: CarPlayNowPlaying, cover: ByteArray?, playing: Boolean) {
        if (!AionIntegration.enabled(context) || !AionIntegration.supported()) return
        val normalized = info.copy(elapsedMillis = null, playing = playing)
        val previous = latest
        if (previous?.owner === owner && previous.info == normalized && sameBytes(previous.cover, cover)) return
        val update = Update(context.applicationContext, owner, normalized,
            if (sameBytes(previous?.cover, cover)) previous?.cover else cover?.clone())
        latest = update
        if (prefs(context).getBoolean(ENABLED, false)) worker.execute { publishCurrent(update) }
    }

    @JvmStatic fun changed(context: Context, enabled: Boolean) {
        val current = latest
        if (enabled && current != null) worker.execute { publishCurrent(current, force = true) }
        else if (!enabled) worker.execute { reset(context) }
    }

    @JvmStatic @Synchronized fun clear(context: Context, owner: Any) {
        if (latest?.owner !== owner) return
        latest = null
        worker.execute { if (latest == null) reset(context) }
    }

    private fun matchedCar(context: Context): Boolean {
        if (!AionIntegration.supported()) return false
        val target = context.packageManager.getPackageInfo(AionDevicePolicy.MUSIC_PACKAGE, 0)
        return target.applicationInfo?.uid == 1000 && target.versionName == "V_20220814_1359_CONFOUND"
    }

    private fun publishCurrent(update: Update, force: Boolean = false) {
        val context = update.context
        if (latest !== update || !prefs(context).getBoolean(ENABLED, false)) return
        try {
            check(matchedCar(context)) { "原车仪表程序版本不匹配" }
            val title = update.info.title?.trim().orEmpty()
            if (!AionDevicePolicy.validTitle(title)) return
            if (sentOwner !== update.owner || force) { lastTitle = ""; lastState = -1; lastCover = null; sentOwner = update.owner }
            val uri = prepareCover(context, update.cover)
            if (latest !== update) return
            if (lastTitle != title) {
                send(context, 101, Bundle().apply { putString("sender", context.packageName); putString("mediaName", title) })
                if (lastTitle.isEmpty()) send(context, 105, Bundle().apply { putString("mediaAppName", "DiPlay") })
                lastTitle = title
            }
            val state = if (update.info.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            if (state != lastState) { send(context, 103, Bundle().apply { putInt("state", state) }); lastState = state }
            if (uri != lastCover) {
                send(context, 104, Bundle().apply { putString("albumPath", uri?.toString().orEmpty()) })
                lastCover = uri
            }
            prefs(context).edit().putString("title", title).putBoolean("cover", uri != null)
                .putString(STATUS, "已直接输出内部歌曲信息${if (uri != null) "和封面文件" else ""}，仪表效果待核验${artworkDiagnostic?.let { "；$it" }.orEmpty()}").apply()
        } catch (error: Exception) {
            prefs(context).edit().putString(STATUS, "仪表歌曲输出未完成：${error.javaClass.simpleName} ${error.message}").apply()
        }
    }

    private fun prepareCover(context: Context, bytes: ByteArray?): Uri? {
        if (sameBytes(preparedBytes, bytes) && preparedFile?.isFile == true) return preparedUri
        artworkDiagnostic = null
        preparedBytes = null; preparedUri = null; preparedFile = null
        if (bytes == null || bytes.isEmpty()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            artworkDiagnostic = "封面尺寸无法识别：${options.outWidth}×${options.outHeight}"
            return null
        }
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 512) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: run {
            artworkDiagnostic = "封面解码未完成"; return null
        }
        val scale = minOf(1f, 256f / maxOf(bitmap.width, bitmap.height))
        val reduced = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()), true)
        val png = try { ByteArrayOutputStream().also { check(reduced.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray() }
            finally { if (reduced !== bitmap) reduced.recycle(); bitmap.recycle() }
        if (png.size > 262144) { artworkDiagnostic = "封面转换后超出大小限制：${png.size}"; return null }
        val hash = MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, "aion-instrument-art").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "$hash.png")
        if (!file.exists()) {
            val staging = File(directory, "$hash.tmp")
            staging.writeBytes(png)
            check(staging.renameTo(file)) { "无法保存仪表封面" }
        }
        val uri = InstrumentArtworkProvider.uri(context, file)
        context.grantUriPermission(AionDevicePolicy.MUSIC_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Keep the latest two covers so an in-flight OEM image read can complete.
        directory.listFiles()?.filter { it.name.matches(Regex("[0-9a-f]{64}\\.png")) &&
            it.name != file.name && it.name != lastCover?.lastPathSegment }?.forEach { old ->
                val oldUri = InstrumentArtworkProvider.uri(context, old)
                context.revokeUriPermission(oldUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                old.delete()
            }
        preparedBytes = bytes; preparedUri = uri; preparedFile = file
        return uri
    }

    private fun send(context: Context, command: Int, data: Bundle) {
        context.sendBroadcast(Intent("com.desay_sv.cluster.music.action").setPackage(AionDevicePolicy.MUSIC_PACKAGE)
            .putExtra("cluster_cmd", command).putExtra("cluster_bundle", data))
    }

    private fun reset(context: Context) {
        if (sentOwner != null) runCatching {
            send(context, 103, Bundle().apply { putInt("state", PlaybackState.STATE_STOPPED) })
            send(context, 104, Bundle().apply { putString("albumPath", "") })
        }
        sentOwner = null; lastTitle = ""; lastState = -1; lastCover = null
    }

    private fun sameBytes(left: ByteArray?, right: ByteArray?): Boolean = left === right || (left != null && right != null && left.contentEquals(right))
}
