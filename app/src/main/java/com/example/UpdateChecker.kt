package com.example.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {

    private const val GITHUB_OWNER = "diademma"
    private const val GITHUB_REPO = "nan0gram"
    private const val API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private val client = OkHttpClient()

    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(API_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                val latestTag = json.getString("tag_name").removePrefix("v")
                val releaseNotes = json.optString("body", "")

                // Ищем APK в assets релиза
                val assets = json.getJSONArray("assets")
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                // Если APK не найден — открываем страницу релиза
                if (apkUrl.isEmpty()) {
                    apkUrl = json.getString("html_url")
                }

                val isUpdateAvailable = isNewerVersion(latestTag, currentVersionName)

                UpdateInfo(
                    latestVersion = latestTag,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    isUpdateAvailable = isUpdateAvailable
                )
            } catch (e: Exception) {
                null
            }
        }

    fun openDownloadPage(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
