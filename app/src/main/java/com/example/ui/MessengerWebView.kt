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
            
            // argb(1, 0, 0, 0) вместо TRANSPARENT решает аппаратный баг WebView,
            // при котором видео или его контейнер не отрисовываются на полностью прозрачном фоне
            setBackgroundColor(android.graphics.Color.argb(1, 0, 0, 0))
            
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
                                    // 1. Базовое определение по расширению
                                    var mimeType = when {
                                        fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                                        fileName.endsWith(".webm", ignoreCase = true) -> "video/webm"
                                        fileName.endsWith(".ogg", ignoreCase = true) -> "video/ogg"
                                        fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                                        fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                                        fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                                        fileName.endsWith(".png", ignoreCase = true) -> "image/png"
                                        else -> "application/octet-stream"
                                    }

                                    // 2. Нативный детектор Magic Bytes (сигнатур). Решает критический баг проекта,
                                    // при котором база данных сохраняет WebM-видео под расширением .mp4.
                                    try {
                                        java.io.FileInputStream(file).use { fis ->
                                            val header = ByteArray(12)
                                            val read = fis.read(header)
                                            if (read >= 4) {
                                                val h0 = header[0].toInt() and 0xFF
                                                val h1 = header[1].toInt() and 0xFF
                                                val h2 = header[2].toInt() and 0xFF
                                                val h3 = header[3].toInt() and 0xFF
                                                
                                                mimeType = when {
                                                    // PNG: 89 50 4E 47
                                                    h0 == 0x89 && h1 == 0x50 && h2 == 0x4E && h3 == 0x47 -> "image/png"
                                                    // JPEG: FF D8 FF
                                                    h0 == 0xFF && h1 == 0xD8 && h2 == 0xFF -> "image/jpeg"
                                                    // GIF: 47 49 46 38 ("GIF8")
                                                    h0 == 0x47 && h1 == 0x49 && h2 == 0x46 && h3 == 0x38 -> "image/gif"
                                                    // WebM / EBML: 1A 45 DF A3
                                                    h0 == 0x1A && h1 == 0x45 && h2 == 0xDF && h3 == 0xA3 -> "video/webm"
                                                    // Ogg: 4F 67 67 53 ("OggS")
                                                    h0 == 0x4F && h1 == 0x67 && h2 == 0x67 && h3 == 0x53 -> "video/ogg"
                                                    // MP4: "ftyp" на смещении 4
                                                    read >= 8 && header[4].toChar() == 'f' && header[5].toChar() == 't' && header[6].toChar() == 'y' && header[7].toChar() == 'p' -> "video/mp4"
                                                    else -> mimeType
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Игнорируем ошибки чтения сигнатуры, откатываемся к базовому Mime-Type
                                    }

                                    val headers = request.requestHeaders
                                    val rangeHeader = headers?.entries?.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
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
                                                put("Content-Type", mimeType)
                                                put("Content-Range", "bytes */$totalLength")
                                                put("Access-Control-Allow-Origin", "*")
                                            }
                                            return WebResourceResponse(
                                                mimeType, null, 416, 
                                                "Requested Range Not Satisfiable", responseHeaders, 
                                                java.io.ByteArrayInputStream(ByteArray(0))
                                            )
                                        }

                                        if (end >= totalLength) {
                                            end = totalLength - 1
                                        }

                                        val chunkLength = (end - start + 1).toInt()
                                        val bytes = ByteArray(chunkLength)
                                        
                                        // Читаем строго запрошенный чанк напрямую в оперативную память.
                                        // Это полностью устраняет любые проблемы с блокировками дескрипторов файлов
                                        // и багами skip()/available() в WebView.
                                        java.io.RandomAccessFile(file, "r").use { raf ->
                                            raf.seek(start)
                                            raf.readFully(bytes)
                                        }

                                        val responseHeaders = mutableMapOf<String, String>().apply {
                                            put("Content-Type", mimeType)
                                            put("Accept-Ranges", "bytes")
                                            put("Content-Range", "bytes $start-$end/$totalLength")
                                            // Content-Length ОБЯЗАТЕЛЕН для 206: без него Chromium-плеер
                                            // не знает границу чанка и зацикливается на одном диапазоне.
                                            put("Content-Length", chunkLength.toString())
                                            put("Access-Control-Allow-Origin", "*")
                                        }

                                        log("[MediaManager] Стриминг чанка: $start-$end/$totalLength ($fileName) [Mime: $mimeType]")
                                        return WebResourceResponse(
                                            mimeType, null, 206, "Partial Content", 
                                            responseHeaders, java.io.ByteArrayInputStream(bytes)
                                        )
                                    } else {
                                        val bytes = file.readBytes()
                                        val responseHeaders = mutableMapOf<String, String>().apply {
                                            put("Content-Type", mimeType)
                                            put("Accept-Ranges", "bytes")
                                            put("Content-Length", bytes.size.toString())
                                            put("Access-Control-Allow-Origin", "*")
                                        }
                                        log("[MediaManager] Полный файл: $fileName (${bytes.size} байт) [Mime: $mimeType]")
                                        return WebResourceResponse(
                                            mimeType, null, 200, "OK", 
                                            responseHeaders, java.io.ByteArrayInputStream(bytes)
                                        )
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
                    
                    // Принудительно заставляем все <video> элементы рендериться в отдельном 3D-слое.
                    // Это лечит нативный баг Chromium, при котором видео становится прозрачным/невидимым (пустота)
                    // на фоне glassmorphic-окон мессенджера.
                    view?.evaluateJavascript("""
                        (function(){
                            var style = document.createElement('style');
                            style.type = 'text/css';
                            style.innerHTML = 'video { transform: translateZ(0) !important; will-change: transform !important; }';
                            document.getElementsByTagName('head')[0].appendChild(style);
                            console.log('[CSS Patch] Video hardware layer patch injected');
                        })();
                    """.trimIndent(), null)
                    
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