package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object StealthCache {
    var pendingUris: Array<Uri>? = null
    var pendingSysBlock: String? = null
}

internal fun getUriSize(context: Context, uri: Uri): Long {
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex != -1) {
                return cursor.getLong(sizeIndex)
            }
        }
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            return fd.statSize
        }
    } catch (e: Exception) {}
    return 0L
}

internal fun getOriginalFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        } catch (_: Exception) {} finally { cursor?.close() }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) result = result.substring(cut + 1)
    }
    return result ?: "media_file"
}

internal fun createStealthCopy(context: Context, originalUri: Uri): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(originalUri) ?: return null
        val originalName = getOriginalFileName(context, originalUri)
        val baseName = if (originalName.contains(".")) originalName.substringBeforeLast(".") else originalName
        val file = File(context.cacheDir, "${baseName}.bin")
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        Uri.fromFile(file)
    } catch (e: Exception) { null }
}

internal fun createEncryptedStealthCopy(context: Context, originalUri: Uri, keyStr: String): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(originalUri) ?: return null
        val originalName = getOriginalFileName(context, originalUri)
        val baseName = if (originalName.contains(".")) originalName.substringBeforeLast(".") else originalName
        val file = File(context.cacheDir, "${baseName}.bin")
        
        val keyBytes = "$keyStr:media".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes32 = digest.digest(keyBytes)
        val secretKey = SecretKeySpec(keyBytes32, "AES")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        
        val ciphertext = cipher.doFinal(inputStream.readBytes())
        inputStream.close()
        
        val outputStream = FileOutputStream(file)
        outputStream.write(iv)
        outputStream.write(ciphertext)
        outputStream.flush()
        outputStream.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        Log.e("nan0gram", "Encryption failed: ${e.message}")
        null
    }
}

internal fun uriToBase64(context: Context, uri: Uri): String {
    return try {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        if (mimeType.startsWith("video")) {
            val bytes = inputStream.readBytes()
            inputStream.close()
            return "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()
        val maxDimension = 800
        var scale = 1
        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / scale) >= maxDimension && (halfWidth / scale) >= maxDimension) scale *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
        val secondStream = context.contentResolver.openInputStream(uri) ?: return ""
        val bitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions) ?: return ""
        secondStream.close()
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        bitmap.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) { "" }
}

internal fun getVideoThumbnailBase64(context: Context, uri: Uri): String {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return ""
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        bitmap.recycle()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) { "" } finally { try { retriever.release() } catch (_: Exception) {} }
}