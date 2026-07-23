package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder

internal class MessengerDownloadHelper(
    private val log: (String) -> Unit,
    private val getScope: () -> CoroutineScope,
    private val getMessengerWebView: () -> WebView?,
    private val getUkrnetWebView: () -> WebView?,
    private val mediaManager: () -> MediaManager?
) {
    private val ui = Handler(Looper.getMainLooper())

    fun saveMediaToDownloads(urlOrBase64: String, suggestedName: String) {
        val context = getMessengerWebView()?.context ?: getUkrnetWebView()?.context ?: run {
            log("[Download Error] Context is null for saveMediaToDownloads")
            return
        }
        val mm = mediaManager() ?: run {
            log("[Download Error] MediaManager is null for saveMediaToDownloads")
            return
        }
        log("[MediaManager] Запуск сохранения медиафайла $suggestedName в Загрузки...")
        getScope().launch(Dispatchers.IO) {
            try {
                var bytes: ByteArray? = null
                var mimeType: String? = null
                var finalName = suggestedName.ifEmpty { "downloaded_file" }
                var fileExt = ""

                if (urlOrBase64.startsWith("data:")) {
                    val parts = urlOrBase64.split(",")
                    if (parts.size > 1) {
                        val header = parts[0]
                        mimeType = header.substringAfter("data:").substringBefore(";base64")
                        fileExt = when {
                            mimeType.contains("video") -> "mp4"
                            mimeType.contains("audio") || mimeType.contains("webm") -> "webm"
                            mimeType.contains("image") -> "jpg"
                            else -> "bin"
                        }
                        var cleanB64 = parts[1]
                        if (cleanB64.contains("%")) {
                            try {
                                cleanB64 = URLDecoder.decode(cleanB64, "UTF-8")
                            } catch (e: Exception) {}
                        }
                        cleanB64 = cleanB64.replace(" ", "+")
                        bytes = Base64.decode(cleanB64.trim(), Base64.DEFAULT)
                    }
                } else if (urlOrBase64.contains("appassets.androidlocal/media/")) {
                    var filename = urlOrBase64.substringAfter("appassets.androidlocal/media/")
                    try {
                        filename = URLDecoder.decode(filename, "UTF-8")
                    } catch (e: Exception) {}
                    val file = File(mm.getMediaDir(), filename)
                    if (file.exists()) {
                        bytes = file.readBytes()
                        fileExt = file.extension
                        mimeType = context.contentResolver.getType(Uri.fromFile(file))
                    }
                } else {
                    val cleanPath = urlOrBase64.replace("file://", "")
                    val file = File(cleanPath)
                    if (file.exists()) {
                        bytes = file.readBytes()
                        fileExt = file.extension
                        mimeType = context.contentResolver.getType(Uri.fromFile(file))
                    }
                }

                if (bytes == null) {
                    showNativeSuccessPopup(getMessengerWebView(), "Ошибка: файл не найден ❌")
                    return@launch
                }

                if (fileExt.isEmpty()) {
                    fileExt = when {
                        finalName.endsWith(".jpg") || finalName.endsWith(".jpeg") -> "jpg"
                        finalName.endsWith(".png") -> "png"
                        finalName.endsWith(".mp4") -> "mp4"
                        finalName.endsWith(".webm") -> "webm"
                        else -> "bin"
                    }
                }

                mimeType = when (fileExt.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "png"
                    "mp4" -> "video/mp4"
                    "webm" -> "audio/webm"
                    else -> "application/octet-stream"
                }

                val ext = when (mimeType) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "video/mp4" -> "mp4"
                    "audio/webm" -> "webm"
                    else -> "bin"
                }

                val baseName = finalName.substringBeforeLast(".")
                finalName = "$baseName.$ext"

                val success = saveBytesToDownloadsFolder(context, bytes, finalName, mimeType)
                if (success) {
                    val russianLabel = when (ext) {
                        "jpg", "png" -> "Фотография сохранена в загрузки 🖼️"
                        "mp4" -> "Видео сохранено в загрузки 🎬"
                        "webm" -> "Голосовое сообщение сохранено в загрузки 🎵"
                        else -> "Файл сохранен в загрузки 📄"
                    }
                    showNativeSuccessPopup(getMessengerWebView(), "$russianLabel<br><small style='opacity:0.6;font-size:11px;'>$finalName</small>")
                } else {
                    showNativeSuccessPopup(getMessengerWebView(), "Не удалось сохранить файл ❌")
                }
            } catch (e: Exception) {
                log("[Download Error] Failed to save media: ${e.message}")
                showNativeSuccessPopup(getMessengerWebView(), "Ошибка сохранения файла ❌")
            }
        }
    }

    private fun showNativeSuccessPopup(webView: WebView?, msg: String) {
        val popupJs = """
            (function(){
                var div = document.createElement('div');
                div.innerHTML = "$msg";
                div.style.cssText = 'position:fixed; top:50%; left:50%; transform:translate(-50%, -50%) scale(0.9); background:rgba(46, 29, 60, 0.95); backdrop-filter:blur(15px); -webkit-backdrop-filter:blur(15px); border:1px solid rgba(167, 115, 209, 0.4); box-shadow: 0 0 35px rgba(167, 115, 209, 0.6); color:#fff; padding:22px 26px; border-radius:18px; font-size:15px; text-align:center; z-index:9999; opacity:0; transition:all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275); pointer-events:none; font-weight:500; min-width:240px; line-height:1.4;';
                document.body.appendChild(div);
                requestAnimationFrame(function() {
                    div.style.opacity = '1';
                    div.style.transform = 'translate(-50%, -50%) scale(1)';
                });
                setTimeout(function() {
                    div.style.opacity = '0';
                    div.style.transform = 'translate(-50%, -50%) scale(0.9)';
                    setTimeout(function() { div.remove(); }, 400);
                }, 4000);
            })();
        """.trimIndent()
        ui.post {
            webView?.evaluateJavascript(popupJs, null)
        }
    }

    private fun saveBytesToDownloadsFolder(context: Context, bytes: ByteArray, fileName: String, mimeType: String): Boolean {
        val resolver = context.contentResolver
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                var uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                }
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                        outputStream.flush()
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    return true
                }
            }
        } catch (e: Exception) {
            // fall through to legacy method
        }

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            }
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(file)
            context.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}