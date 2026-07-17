package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
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

    suspend fun checkAndDownloadWebUpdate(context: Context, log: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("nan0gram_web_update_prefs", Context.MODE_PRIVATE)
            
            // 1. Сброс кэша при обновлении версии самого приложения (APK) во избежание конфликтов
            val currentApkVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            } catch (e: Exception) {
                0
            }
            val lastApkVersion = prefs.getInt("apk_version", 0)
            val webAssetsDir = File(context.filesDir, "web_assets")

            if (lastApkVersion != currentApkVersion) {
                log("[OTA] Сброс локального кэша веб-ресурсов (версия APK изменилась: $lastApkVersion -> $currentApkVersion).")
                if (webAssetsDir.exists()) {
                    webAssetsDir.deleteRecursively()
                }
                prefs.edit()
                    .putInt("apk_version", currentApkVersion)
                    .putString("web_version", "default_apk")
                    .apply()
            }

            // 2. Запрос к GitHub API для получения метаданных последнего релиза
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                log("[OTA] Проверка веб-обновлений завершилась с ошибкой: HTTP ${response.code}")
                return@withContext
            }

            val body = response.body?.string() ?: return@withContext
            val json = JSONObject(body)
            val latestTagName = json.getString("tag_name")
            
            val currentWebVersion = prefs.getString("web_version", "default_apk") ?: "default_apk"
            if (latestTagName == currentWebVersion) {
                log("[OTA] Локальная веб-версия актуальна (текущая: $currentWebVersion).")
                return@withContext
            }

            // 3. Поиск файла архива web_update.zip в ассетах релиза
            val assets = json.optJSONArray("assets") ?: return@withContext
            var webUpdateUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == "web_update.zip") {
                    webUpdateUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (webUpdateUrl.isEmpty()) {
                log("[OTA] Релиз $latestTagName не содержит архива web_update.zip.")
                return@withContext
            }

            log("[OTA] Обнаружено новое веб-обновление: $latestTagName. Начинаем скачивание...")

            // 4. Скачивание ZIP-архива во временную папку кэша
            val downloadRequest = Request.Builder().url(webUpdateUrl).build()
            val downloadResponse = client.newCall(downloadRequest).execute()
            if (!downloadResponse.isSuccessful) {
                log("[OTA] Не удалось скачать web_update.zip: HTTP ${downloadResponse.code}")
                return@withContext
            }

            val tempZipFile = File(context.cacheDir, "web_update.temp.zip")
            downloadResponse.body?.byteStream()?.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 5. Распаковка архива во внутреннюю память
            if (!webAssetsDir.exists()) {
                webAssetsDir.mkdirs()
            }
            
            unzip(tempZipFile, webAssetsDir)
            tempZipFile.delete()

            // 6. Запись обновленной версии веб-ресурсов
            prefs.edit()
                .putString("web_version", latestTagName)
                .apply()

            log("[OTA] Локальные веб-ресурсы успешно обновлены до версии $latestTagName. Перезапустите приложение.")
        } catch (e: Exception) {
            log("[OTA Error] Сбой веб-обновления: ${e.message}")
            Log.e(TAG, "OTA update failed", e)
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            val destCanonicalPath = destDir.canonicalPath + File.separator
            while (entry != null) {
                val file = File(destDir, entry.name)
                // Защита от уязвимости внедрения путей (Zip Slip)
                if (!file.canonicalPath.startsWith(destCanonicalPath)) {
                    throw SecurityException("Blocked Zip Slip exploit attempt for path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { out ->
                        zipIn.copyTo(out)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }
}
