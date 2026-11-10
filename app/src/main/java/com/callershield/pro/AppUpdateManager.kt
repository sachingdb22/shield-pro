package com.callershield.pro

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

data class AppRelease(
    val tag: String,
    val apkUrl: String
)

data class DownloadProgress(
    val status: Int,
    val percent: Int
)

object AppUpdateManager {
    private const val RELEASE_API =
        "https://api.github.com/repos/sachingdb22/shield-pro/releases/latest"

    fun findUpdate(currentVersion: String): AppRelease? {
        val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "CallerShieldPro")
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw NoPublishedReleaseException()
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub returned HTTP ${connection.responseCode}")
            }
            val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val tag = release.getString("tag_name")
            if (compareVersions(tag, currentVersion) <= 0) return null

            val assets = release.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.getString("browser_download_url")
                ?: throw IOException("The latest GitHub release does not include an APK")
            if (!isTrustedReleaseUrl(apkUrl)) {
                throw IOException("The release APK URL is not from this GitHub repository")
            }
            return AppRelease(tag, apkUrl)
        } catch (error: JSONException) {
            throw IOException("GitHub returned invalid release information", error)
        } finally {
            connection.disconnect()
        }
    }

    fun enqueueDownload(context: Context, release: AppRelease): Long {
        if (!isTrustedReleaseUrl(release.apkUrl)) {
            throw IOException("The release APK URL is not from this GitHub repository")
        }
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("Caller Shield Pro update ${release.tag}")
            .setDescription("Downloading the latest app update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "CallerShieldPro-update.apk"
            )
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: throw IOException("Android download service is unavailable")
        return manager.enqueue(request)
    }

    fun getDownloadProgress(context: Context, downloadId: Long): DownloadProgress? {
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: throw IOException("Android download service is unavailable")
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
            return DownloadProgress(status, percent)
        }
    }

    fun getDownloadedUri(context: Context, downloadId: Long): Uri? {
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: throw IOException("Android download service is unavailable")
        return manager.getUriForDownloadedFile(downloadId)
    }

    fun getDownloadedApkFile(context: Context): File {
        val downloadsDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IOException("App update storage is unavailable")
        return File(downloadsDirectory, "CallerShieldPro-update.apk")
    }

    private fun isTrustedReleaseUrl(value: String): Boolean {
        val url = try {
            URL(value)
        } catch (_: MalformedURLException) {
            return false
        }
        return url.protocol == "https" &&
            url.host.equals("github.com", ignoreCase = true) &&
            url.path.startsWith("/sachingdb22/shield-pro/releases/download/")
    }

    private fun compareVersions(left: String, right: String): Int {
        fun components(version: String) =
            Regex("\\d+").findAll(version).map { it.value.toLongOrNull() ?: 0L }.toList()
        val leftParts = components(left)
        val rightParts = components(right)
        val count = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until count) {
            val comparison = (leftParts.getOrElse(index) { 0L })
                .compareTo(rightParts.getOrElse(index) { 0L })
            if (comparison != 0) return comparison
        }
        return 0
    }

    class NoPublishedReleaseException : IOException(
        "No published GitHub release is available yet. Publish a release with an APK to enable updates."
    )
}
