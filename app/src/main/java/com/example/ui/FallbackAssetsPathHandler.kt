package com.example

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream

internal class FallbackAssetsPathHandler(
    private val context: Context,
    private val localDir: File
) : WebViewAssetLoader.PathHandler {
    private val assetsHandler = WebViewAssetLoader.AssetsPathHandler(context)

    override fun handle(path: String): WebResourceResponse? {
        val localFile = File(localDir, path)
        if (localFile.exists() && localFile.isFile) {
            try {
                val mimeType = getMimeType(path)
                val inputStream = FileInputStream(localFile)
                // OTA-файлы (.js/.css) никогда не кэшируем: они могут обновиться через OTA
                // в любой момент пока приложение запущено. APK-ассеты кэшируются нормально
                // (они неизменны до следующего обновления APK).
                val headers: Map<String, String> = if (isScriptOrStyle(path)) noCacheHeaders() else emptyMap()
                return WebResourceResponse(mimeType, "UTF-8", 200, "OK", headers, inputStream)
            } catch (e: Exception) {
                // Игнорируем ошибки чтения и плавно переходим к базовым ассетам из APK
            }
        }
        return assetsHandler.handle(path)
    }

    // Скрипты и стили не кэшируем из OTA-директории — их заменяет веб-обновление
    private fun isScriptOrStyle(path: String) =
        path.endsWith(".js", ignoreCase = true) || path.endsWith(".css", ignoreCase = true)

    // Заголовки запрета кэша: три уровня — HTTP/1.1, HTTP/1.0, и явный срок 0
    private fun noCacheHeaders(): Map<String, String> = mapOf(
        "Cache-Control" to "no-store, no-cache, must-revalidate",
        "Pragma"        to "no-cache",
        "Expires"       to "0"
    )

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html", ignoreCase = true) -> "text/html"
            path.endsWith(".css", ignoreCase = true) -> "text/css"
            path.endsWith(".js", ignoreCase = true) -> "application/javascript"
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            path.endsWith(".gif", ignoreCase = true) -> "image/gif"
            path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
            path.endsWith(".json", ignoreCase = true) -> "application/json"
            path.endsWith(".woff2", ignoreCase = true) -> "font/woff2"
            path.endsWith(".woff", ignoreCase = true) -> "font/woff"
            path.endsWith(".ttf", ignoreCase = true) -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}