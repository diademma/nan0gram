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

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? =
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

                val tag = json.getString("tag_name")
                val releaseNotes = json.optString("body", "")

                // Из тега типа "build-42" или "v1.2" вытаскиваем число
                val tagNumber = Regex("\\d+").findAll(tag).lastOrNull()
                    ?.value?.toIntOrNull() ?: 0

                // Ищем APK в assets релиза
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
                    latestVersion = tag,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    isUpdateAvailable = tagNumber > currentVersionCode
                )
            } catch (e: Exception) {
                null
            }
        }

    fun openDownloadPage(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
