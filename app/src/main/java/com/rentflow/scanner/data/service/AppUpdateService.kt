package com.rentflow.scanner.data.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.rentflow.scanner.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: String,
    val size: Long = 0,
)

@Singleton
class AppUpdateService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AppUpdateService"
        private const val GITHUB_REPO = "jeckersberger/RentFlow_scanner"
        private const val RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable

    private val _downloadProgress = MutableStateFlow(-1) // -1 = not downloading
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private var downloadId: Long = -1

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != 200) {
                Log.w(TAG, "GitHub API returned ${connection.responseCode}")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = org.json.JSONObject(response)

            val tagName = json.getString("tag_name") // e.g. "v1.2"
            val changelog = json.optString("body", "")
            val assets = json.getJSONArray("assets")

            // Find the APK asset
            var apkUrl: String? = null
            var apkSize: Long = 0
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    apkSize = asset.getLong("size")
                    break
                }
            }

            if (apkUrl == null) {
                Log.w(TAG, "No APK found in release $tagName")
                return@withContext null
            }

            // Parse version from tag (v1.2 -> 1.2)
            val remoteVersion = tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            // Compare versions
            if (isNewerVersion(remoteVersion, currentVersion)) {
                val info = UpdateInfo(
                    versionName = remoteVersion,
                    versionCode = parseVersionCode(remoteVersion),
                    downloadUrl = apkUrl,
                    changelog = changelog,
                    size = apkSize,
                )
                _updateAvailable.value = info
                Log.d(TAG, "Update available: $currentVersion -> $remoteVersion")
                return@withContext info
            } else {
                Log.d(TAG, "App is up to date ($currentVersion)")
                _updateAvailable.value = null
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            return@withContext null
        }
    }

    fun downloadAndInstall(updateInfo: UpdateInfo) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Clean up old APKs
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadDir?.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }

        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            .setTitle("RentFlow Scanner v${updateInfo.versionName}")
            .setDescription("Update wird heruntergeladen...")
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "rentflow-scanner-${updateInfo.versionName}.apk"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        downloadId = downloadManager.enqueue(request)
        _downloadProgress.value = 0

        // Register receiver for download complete
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        _downloadProgress.value = 100
                        installApk(updateInfo.versionName)
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                    }
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun installApk(version: String) {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "rentflow-scanner-$version.apk"
        )
        if (!file.exists()) {
            Log.e(TAG, "Downloaded APK not found: ${file.absolutePath}")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersionCode(version: String): Int {
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
        return parts.getOrElse(0) { 0 } * 1000 + parts.getOrElse(1) { 0 }
    }
}
