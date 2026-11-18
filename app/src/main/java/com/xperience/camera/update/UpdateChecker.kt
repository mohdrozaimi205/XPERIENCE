package com.xperience.camera.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xperience.camera.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,      // cth "1.0.7"
    val apkUrl: String,
    val apkName: String,
    val notes: String,
    val sizeBytes: Long,
)

/**
 * Semak versi baru di GitHub Releases (hanya baca maklumat release —
 * TIADA gambar atau data peribadi dihantar).
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/mohdrozaimi205/XPERIENCE/releases/latest"
    private const val CHANNEL = "updates"
    private const val NOTIF_ID = 7001
    const val EXTRA_DOWNLOAD = "download_update"

    fun currentVersion(ctx: Context): String = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (_: Exception) { "0" }

    /** "v1.0.12" → [1,0,12] */
    private fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('.', '-').mapNotNull { it.toIntOrNull() }

    fun isNewer(remote: String, local: String): Boolean {
        val a = parse(remote); val b = parse(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "XPERIENCE-Android")
            }
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            if (json.optBoolean("prerelease") || json.optBoolean("draft")) return@withContext null
            val tag = json.optString("tag_name")
            if (!isNewer(tag, currentVersion(ctx))) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk")) {
                    return@withContext UpdateInfo(
                        version = tag.removePrefix("v"),
                        apkUrl = a.optString("browser_download_url"),
                        apkName = name,
                        notes = json.optString("body").take(400),
                        sizeBytes = a.optLong("size"),
                    )
                }
            }
            null
        } catch (_: Exception) { null }
    }

    /** Notifikasi sistem: "Versi baru tersedia" → ketik untuk muat turun. */
    fun notify(ctx: Context, info: UpdateInfo) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(ctx, MainActivity::class.java).putExtra(EXTRA_DOWNLOAD, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("XPERIENCE v${info.version} tersedia")
            .setContentText("Ketik untuk muat turun versi baru (${info.sizeBytes / 1_000_000} MB)")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) } catch (_: SecurityException) { }
    }

    /** Muat turun APK dengan DownloadManager, kemudian buka pemasang. */
    fun download(ctx: Context, info: UpdateInfo) {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("XPERIENCE v${info.version}")
            .setDescription("Memuat turun kemas kini")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, info.apkName)
        val id = dm.enqueue(req)
        val app = ctx.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                try { app.unregisterReceiver(this) } catch (_: Exception) { }
                install(app, dm.getUriForDownloadedFile(id) ?: return)
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED)
    }

    private var pendingInstall: Uri? = null

    /** Dipanggil semasa onResume: sambung pemasangan selepas pengguna beri kebenaran. */
    fun resumePending(ctx: Context) {
        val u = pendingInstall ?: return
        if (Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()) {
            pendingInstall = null
            install(ctx, u)
        }
    }

    fun install(ctx: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            pendingInstall = uri
            // Minta kebenaran "pasang apl tidak diketahui" sekali sahaja
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
