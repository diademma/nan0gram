package com.example

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

    private const val API_URL =
        "https://api.github.com/repos/diademma/nan0gram/releases/latest"

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

                val assets = json.getJSONArray("assets")
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isEmpty()) apkUrl = json.getString("html_url")

                UpdateInfo(
                    latestVersion = latestTag,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    isUpdateAvailable = isNewerVersion(latestTag, currentVersionName)
                )
            } catch (e: Exception) {
                null
            }
        }

    fun openDownloadPage(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val c = current.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(l.size, c.size)) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv > cv) return true
                if (lv < cv) return false
            }
            false
        } catch (e: Exception) { false }
    }
}
