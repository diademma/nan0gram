package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

internal fun buildMessengerWebView(
    ctx: Context,
    messengerInterface: MessengerJsInterface,
    assetLoader: WebViewAssetLoader,
    getMessengerFilePathCallback: () -> ValueCallback<Array<Uri>>?,
    setMessengerFilePathCallback: (ValueCallback<Array<Uri>>?) -> Unit,
    onShowChooser: (ValueCallback<Array<Uri>>?) -> Unit,
    log: (String) -> Unit
): WebView {
    return try {
        WebView(ctx).apply {
            tag = "messenger"
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // Очищаем кэш Chromium при старте, чтобы сбросить агрессивный кэш Chromium и загрузить новые файлы OTA
            clearCache(true)
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
            }
            
            addJavascriptInterface(messengerInterface, "Android")
            
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    request?.url?.let { url ->
                        val urlStr = url.toString()
                        if (urlStr.startsWith("https://appassets.androidlocal/media/")) {
                            try {
                                val fileName = urlStr.substringAfter("https://appassets.androidlocal/media/")
                                val mediaDir = java.io.File(ctx.cacheDir, "nan0gram_media")
                                val file = java.io.File(mediaDir, fileName)
                                
                                if (file.exists() && file.isFile) {
                                    val mimeType = when {
                                        fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                                        fileName.endsWith(".webm", ignoreCase = true) -> "video/webm"
                                        fileName.endsWith(".ogg", ignoreCase = true) -> "video/ogg"
                                        fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                                        fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                                        fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                                        fileName.endsWith(".png", ignoreCase = true) -> "image/png"
                                        else -> "application/octet-stream"
                                    }

                                    val headers = request.requestHeaders
                                    val rangeHeader = headers?.get("Range") ?: headers?.get("range")
                                    val totalLength = file.length()

                                    if (rangeHeader != null) {
                                        var start = 0L
                                        var end = totalLength - 1
                                        try {
                                            if (rangeHeader.startsWith("bytes=")) {
                                                val rangeStr = rangeHeader.substring(6)
                                                val minusIndex = rangeStr.indexOf('-')
                                                if (minusIndex >= 0) {
                                                    val startStr = rangeStr.substring(0, minusIndex).trim()
                                                    val endStr = rangeStr.substring(minusIndex + 1).trim()
                                                    if (startStr.isNotEmpty()) {
                                                        start = startStr.toLong()
                                                    }
                                                    if (endStr.isNotEmpty()) {
                                                        end = endStr.toLong()
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            log("[MediaManager Error] Сбой парсинга Range: ${e.message}")
                                        }

                                        if (start > end || start >= totalLength) {
                                            val responseHeaders = mutableMapOf<String, String>().apply {
                                                put("Content-Range", "bytes */$totalLength")
                                            }
                                            return WebResourceResponse(
                                                mimeType, "UTF-8", 416, 
                                                "Requested Range Not Satisfiable", responseHeaders, 
                                                java.io.ByteArrayInputStream(ByteArray(0))
                                            )
                                        }

                                        if (end >= totalLength) {
                                            end = totalLength - 1
                                        }

                                        val chunkLength = end - start + 1
                                        val responseHeaders = mutableMapOf<String, String>().apply {
                                            put("Accept-Ranges", "bytes")
                                            put("Content-Range", "bytes $start-$end/$totalLength")
                                            put("Content-Length", chunkLength.toString())
                                        }

                                        val fis = java.io.FileInputStream(file)
                                        fis.skip(start)

                                        val rangeInputStream = object : java.io.InputStream() {
                                            private var bytesRead = 0L

                                            override fun read(): Int {
                                                if (bytesRead >= chunkLength) return -1
                                                val byte = fis.read()
                                                if (byte != -1) bytesRead++
                                                return byte
                                            }

                                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                                if (bytesRead >= chunkLength) return -1
                                                val maxToRead = minOf(len.toLong(), chunkLength - bytesRead).toInt()
                                                val read = fis.read(b, off, maxToRead)
                                                if (read != -1) bytesRead += read
                                                return read
                                            }

                                            override fun close() {
                                                fis.close()
                                                super.close()
                                            }
                                        }

                                        log("[MediaManager] Отдан чанк видео: $start-$end/$totalLength ($fileName)")
                                        return WebResourceResponse(mimeType, "UTF-8", 206, "Partial Content", responseHeaders, rangeInputStream)
                                    } else {
                                        val responseHeaders = mutableMapOf<String, String>().apply {
                                            put("Accept-Ranges", "bytes")
                                            put("Content-Length", totalLength.toString())
                                        }
                                        log("[MediaManager] Отдан полный медиа файл: $fileName")
                                        return WebResourceResponse(mimeType, "UTF-8", 200, "OK", responseHeaders, java.io.FileInputStream(file))
                                    }
                                } else {
                                    log("[MediaManager Error] Локальный файл медиа не найден: $fileName")
                                }
                            } catch (e: Exception) {
                                log("[MediaManager Error] Исключение при стриминге медиа: ${e.message}")
                            }
                        }
                        assetLoader.shouldInterceptRequest(url)?.let { return it }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript("""
                        (function(){
                            if(window._n0gDirectPickerPatch)return;
                            window._n0gDirectPickerPatch=true;
                            var _attachDebounce = false;
                            function triggerStealthAttach(mode) {
                                if (_attachDebounce) return;
                                _attachDebounce = true;
                                setTimeout(function(){ _attachDebounce = false; }, 2000);
                                if (window.Android) {
                                    var mk = "";
                                    if (window.nanoUtils && window.nanoUtils.randomKey) {
                                        mk = window.nanoUtils.randomKey();
                                    } else {
                                        var arr = new Uint8Array(16);
                                        window.crypto.getRandomValues(arr);
                                        for(var i=0; i<16; i++) { mk += arr[i].toString(16).padStart(2,'0'); }
                                    }
                                    window.nan0gram_pendingMediaKey = mk;
                                    if (typeof window.Android.prepareForDirectAttachWithMode === 'function') {
                                        window.Android.prepareForDirectAttachWithMode(mk, mode || "media");
                                    } else if (typeof window.Android.prepareForDirectAttach === 'function') {
                                        window.Android.prepareForDirectAttach(mk);
                                    }
                                }
                            }
                            document.addEventListener('touchstart', function(e) {
                                var t = e.target.closest('.input-icon');
                                if (t) {
                                    window._n0gStealthPending = true;
                                    var mode = t.getAttribute('data-mode') || 'media';
                                    triggerStealthAttach(mode);
                                }
                            }, true);
                            document.addEventListener('mousedown', function(e) {
                                var t = e.target.closest('.input-icon');
                                if (t) {
                                    window._n0gStealthPending = true;
                                    var mode = t.getAttribute('data-mode') || 'media';
                                    triggerStealthAttach(mode);
                                }
                            }, true);
                            document.addEventListener('click', function(e) {
                                var t = e.target.closest('.input-icon');
                                if (t) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                }
                            }, true);
                        })();
                    """.trimIndent(), null)
                    
                    view?.evaluateJavascript("""
                        (function polyfillNan0gramFn(){
                            if(window.nan0gram){
                                if(!window.nan0gram._openComposeIfNeeded){
                                    window.nan0gram._openComposeIfNeeded=function(){};
                                    console.log('[Polyfill] _openComposeIfNeeded injected');
                                }
                            } else {
                                setTimeout(polyfillNan0gramFn, 300);
                            }
                        })();
                    """.trimIndent(), null)
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                    val src = m?.sourceId()?.substringAfterLast('/') ?: "?"
                    val line = m?.lineNumber() ?: 0
                    val msg = m?.message() ?: ""
                    when (m?.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR ->
                            log("[❌ JS] $msg ($src:$line)")
                        ConsoleMessage.MessageLevel.WARNING ->
                            log("[⚠️ JS] $msg ($src:$line)")
                        else ->
                            Log.d("n0g_js", "$msg ($src:$line)")
                    }
                    return true
                }
                
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
                
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallbackParams: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (getMessengerFilePathCallback() != null) {
                        filePathCallbackParams?.onReceiveValue(null)
                        return true
                    }
                    setMessengerFilePathCallback(filePathCallbackParams)
                    try {
                        onShowChooser(filePathCallbackParams)
                    } catch (e: Exception) {
                        setMessengerFilePathCallback(null)
                        filePathCallbackParams?.onReceiveValue(null)
                        return false
                    }
                    return true
                }
            }
            loadUrl("https://appassets.androidlocal/assets/index.html")
        }
    } catch (e: Exception) {
        log("[❌ MessengerWebView] Error building Messenger WebView: ${e.message}")
        WebView(ctx)
    }
}