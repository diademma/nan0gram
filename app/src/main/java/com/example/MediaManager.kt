package com.example

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaManager(private val context: Context, private val log: (String) -> Unit) {

    // Переключатель логов медиа-менеджера. false = тихий режим. Поменяй на true чтобы включить.
    private val LOGGING_ENABLED = true

    // Директория для хранения всех картинок, видео и прикрепленных файлов
    private val mediaDir = File(context.cacheDir, "nan0gram_media").apply {
        if (!exists()) mkdirs()
    }

    // Виртуальный домен, который мы позже привяжем к WebViewAssetLoader
    private val VIRTUAL_DOMAIN = "https://appassets.androidlocal/media/"

    /**
     * Сохраняет Base64 (картинку или видео) в кэш и возвращает безопасный виртуальный URL
     */
    suspend fun saveBase64Media(base64String: String, extension: String = "jpg"): String = withContext(Dispatchers.IO) {
        try {
            val cleanBase64 = if (base64String.contains(",")) {
                base64String.substringAfter(",")
            } else {
                base64String
            }

            val bytes = Base64.decode(cleanBase64, Base64.NO_WRAP)
            val fileName = "media_${UUID.randomUUID()}.$extension"
            val file = File(mediaDir, fileName)

            FileOutputStream(file).use { it.write(bytes) }
            
            val virtualUrl = "$VIRTUAL_DOMAIN$fileName"
            if (LOGGING_ENABLED) log("[MediaManager] Сохранен файл $fileName (${bytes.size / 1024} KB)")
            virtualUrl
        } catch (e: Exception) {
            if (LOGGING_ENABLED) log("[MediaManager Error] Ошибка сохранения Base64: ${e.message}")
            ""
        }
    }

    /**
     * Копирует файл из Uri (например, выбранный пользователем документ) в наш кэш
     */
    suspend fun saveFileFromUri(uri: Uri, originalName: String): String = withContext(Dispatchers.IO) {
        try {
            val ext = if (originalName.contains(".")) originalName.substringAfterLast('.') else "dat"
            val fileName = "file_${UUID.randomUUID()}.$ext"
            val file = File(mediaDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            val virtualUrl = "$VIRTUAL_DOMAIN$fileName"
            if (LOGGING_ENABLED) log("[MediaManager] Скопирован файл $originalName -> $fileName (${file.length() / 1024} KB)")
            virtualUrl
        } catch (e: Exception) {
            if (LOGGING_ENABLED) log("[MediaManager Error] Ошибка копирования файла: ${e.message}")
            ""
        }
    }

    /**
     * Очистка всего кэша медиа. Возвращает объем освобожденного места в байтах.
     */
    suspend fun clearMediaCache(): Long = withContext(Dispatchers.IO) {
        try {
            var deletedSize = 0L
            if (mediaDir.exists()) {
                mediaDir.listFiles()?.forEach { file ->
                    deletedSize += file.length()
                    file.delete()
                }
            }
            val mb = deletedSize / (1024 * 1024)
            if (LOGGING_ENABLED) log("[MediaManager] Кэш медиа очищен! Освобождено: $mb MB")
            deletedSize
        } catch (e: Exception) {
            if (LOGGING_ENABLED) log("[MediaManager Error] Ошибка очистки кэша: ${e.message}")
            0L
        }
    }

    /**
     * Возвращает текущий размер папки кэша в байтах (для отображения в UI Настроек)
     */
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        try {
            var size = 0L
            if (mediaDir.exists()) {
                mediaDir.listFiles()?.forEach { size += it.length() }
            }
            size
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Предоставляет доступ к директории для WebViewAssetLoader
     */
    fun getMediaDir(): File = mediaDir
}
