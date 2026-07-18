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
                return WebResourceResponse(mimeType, "UTF-8", inputStream)
            } catch (e: Exception) {
                // Игнорируем ошибки чтения и плавно переходим к базовым ассетам из APK
            }
        }
        return assetsHandler.handle(path)
    }

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