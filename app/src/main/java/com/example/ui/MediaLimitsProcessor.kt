package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView

internal fun processUrisWithLimits(
    context: Context,
    rawUris: List<Uri>,
    messengerInterface: MessengerJsInterface,
    messengerWebView: WebView?,
    onResult: (List<Uri>) -> Unit
) {
    try {
        if (messengerInterface.pendingStealthMode == "file") {
            val maxSizeBytes = 17 * 1024 * 1024L
            val validUris = mutableListOf<Uri>()
            var droppedCount = 0
            var sizeDropped = false
            var countDropped = false
            
            for (uri in rawUris) {
                if (validUris.size >= 1) {
                    countDropped = true
                    droppedCount++
                    continue
                }
                val fileSize = getUriSize(context, uri)
                if (fileSize > maxSizeBytes) {
                    sizeDropped = true
                    droppedCount++
                    continue
                }
                validUris.add(uri)
            }
            
            if (droppedCount > 0 && messengerWebView != null) {
                val msg = buildString {
                    append("Упс! Сработали лимиты 🤫<br><br>")
                    if (countDropped) append("Максимум 1 файл за раз.<br>")
                    if (sizeDropped) append("Превышен лимит размера (17 МБ).<br>")
                    append("<br>Отсечено файлов: <b>$droppedCount</b>")
                }
                val popupJs = """
                    (function(){
                        var div = document.createElement('div');
                        div.innerHTML = '$msg';
                        div.style.cssText = 'position:fixed; top:50%; left:50%; transform:translate(-50%, -50%) scale(0.9); background:rgba(46, 29, 60, 0.95); backdrop-filter:blur(15px); -webkit-backdrop-filter:blur(15px); border:1px solid rgba(167, 115, 209, 0.4); box-shadow: 0 0 35px rgba(167, 115, 209, 0.6); color:#fff; padding:22px 26px; border-radius:18px; font-size:15px; text-align:center; z-index:9999; opacity:0; transition:all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275); pointer-events:none; font-weight:500; min-width:240px; line-height:1.4;';
                        document.body.appendChild(div);
                        requestAnimationFrame(() => {
                            div.style.opacity = '1';
                            div.style.transform = 'translate(-50%, -50%) scale(1)';
                        });
                        setTimeout(() => {
                            div.style.opacity = '0';
                            div.style.transform = 'translate(-50%, -50%) scale(0.9)';
                            setTimeout(() => div.remove(), 400);
                        }, 5500);
                    })();
                """.trimIndent()
                messengerWebView.post {
                    messengerWebView.evaluateJavascript(popupJs, null)
                }
            }
            onResult(validUris)
        } else {
            val maxPhotos = 8
            val maxVideos = 2
            val maxSizeBytes = 15 * 1024 * 1024L
            
            val validUris = mutableListOf<Uri>()
            var photoCount = 0
            var videoCount = 0
            var totalSize = 0L
            
            var photoDropped = false
            var videoDropped = false
            var sizeDropped = false
            var droppedCount = 0
            
            for (uri in rawUris) {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val isVideo = mimeType.startsWith("video")
                val fileSize = getUriSize(context, uri)
                
                if (isVideo) {
                    if (videoCount >= maxVideos) {
                        videoDropped = true
                        droppedCount++
                        continue
                    }
                } else {
                    if (photoCount >= maxPhotos) {
                        photoDropped = true
                        droppedCount++
                        continue
                    }
                }
                
                if (totalSize + fileSize > maxSizeBytes) {
                    sizeDropped = true
                    droppedCount++
                    continue
                }
                
                validUris.add(uri)
                totalSize += fileSize
                if (isVideo) videoCount++ else photoCount++
            }
            
            if (droppedCount > 0 && messengerWebView != null) {
                val msg = buildString {
                    append("Упс! Сработали лимиты 🤫<br><br>")
                    if (photoDropped) append("Максимум 8 фото.<br>")
                    if (videoDropped) append("Максимум 2 видео.<br>")
                    if (sizeDropped) append("Превышен лимит размера (15 МБ).<br>")
                    append("<br>Отсечено файлов: <b>$droppedCount</b>")
                }
                val popupJs = """
                    (function(){
                        var div = document.createElement('div');
                        div.innerHTML = '$msg';
                        div.style.cssText = 'position:fixed; top:50%; left:50%; transform:translate(-50%, -50%) scale(0.9); background:rgba(46, 29, 60, 0.95); backdrop-filter:blur(15px); -webkit-backdrop-filter:blur(15px); border:1px solid rgba(167, 115, 209, 0.4); box-shadow: 0 0 35px rgba(167, 115, 209, 0.6); color:#fff; padding:22px 26px; border-radius:18px; font-size:15px; text-align:center; z-index:9999; opacity:0; transition:all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275); pointer-events:none; font-weight:500; min-width:240px; line-height:1.4;';
                        document.body.appendChild(div);
                        requestAnimationFrame(() => {
                            div.style.opacity = '1';
                            div.style.transform = 'translate(-50%, -50%) scale(1)';
                        });
                        setTimeout(() => {
                            div.style.opacity = '0';
                            div.style.transform = 'translate(-50%, -50%) scale(0.9)';
                            setTimeout(() => div.remove(), 400);
                        }, 5500);
                    })();
                """.trimIndent()
                messengerWebView.post {
                    messengerWebView.evaluateJavascript(popupJs, null)
                }
            }
            onResult(validUris)
        }
    } catch (e: Exception) {
        Log.e("nan0gram", "[❌ MediaLimitsProcessor] ${e.message}")
        onResult(emptyList())
    }
}