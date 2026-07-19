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

                                    val fileLength = file.length()
                                    val rangeHeader = request.requestHeaders
                                        ?.entries?.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value

                                    // Определяем faststart: обходим атомы с начала файла.
                                    // Если moov встретился раньше mdat — faststart (moov в начале).
                                    // Non-faststart (moov в конце) нельзя обслуживать через 206-чанки:
                                    // Chromium зацикливается ища moov. Им отдаём 200 без Accept-Ranges.
                                    val fastStart = try {
                                        java.io.RandomAccessFile(file, "r").use { raf ->
                                            var offset = 0L; var found = false
                                            for (i in 0 until 8) {
                                                val h = ByteArray(8); raf.seek(offset)
                                                if (raf.read(h) < 8) break
                                                val sz = ((h[0].toInt() and 0xFF) shl 24) or
                                                         ((h[1].toInt() and 0xFF) shl 16) or
                                                         ((h[2].toInt() and 0xFF) shl 8) or
                                                         (h[3].toInt() and 0xFF)
                                                val nm = String(h, 4, 4, Charsets.ISO_8859_1)
                                                if (nm == "moov") { found = true; break }
                                                if (nm == "mdat" || sz < 8 || sz > 50_000_000) break
                                                offset += sz
                                            }
                                            found
                                        }
                                    } catch (e: Exception) { false }

                                    // Seek-запрос: Range с start > 0.
                                    // Только для faststart: Chromium уже имеет moov и seekит в mdat.
                                    // Non-faststart сюда не попадает (нет Accept-Ranges в их 200 OK).
                                    val seekStart = rangeHeader
                                        ?.takeIf { it.startsWith("bytes=") }
                                        ?.substringAfter("bytes=")
                                        ?.substringBefore('-')
                                        ?.trim()?.toLongOrNull()
                                        ?.takeIf { it > 0L }

                                    if (seekStart != null) {
                                        val rangeStr = rangeHeader!!.substringAfter("bytes=")
                                        val minus = rangeStr.indexOf('-')
                                        val start = rangeStr.substring(0, minus).trim().toLongOrNull() ?: 0L
                                        val endStr = rangeStr.substring(minus + 1).trim()
                                        var end = endStr.toLongOrNull() ?: (fileLength - 1)
                                        if (end >= fileLength) end = fileLength - 1
                                        val chunkLength = end - start + 1

                                        val seekStream = object : java.io.InputStream() {
                                            val raf = java.io.RandomAccessFile(file, "r").apply { seek(start) }
                                            var pos = start
                                            override fun read(): Int {
                                                if (pos > end) return -1
                                                val b = raf.read()
                                                if (b >= 0) pos++
                                                return b
                                            }
                                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                                if (pos > end) return -1
                                                val toRead = minOf(len.toLong(), end - pos + 1).toInt()
                                                val n = raf.read(b, off, toRead)
                                                if (n > 0) pos += n
                                                return n
                                            }
                                            override fun close() { raf.close() }
                                        }
                                        val seekHeaders = mutableMapOf<String, String>().apply {
                                            put("Content-Type", mimeType)
                                            put("Accept-Ranges", "bytes")
                                            put("Content-Range", "bytes $start-$end/$fileLength")
                                            put("Content-Length", chunkLength.toString())
                                            put("Cache-Control", "no-cache, no-store")
                                            put("Access-Control-Allow-Origin", "*")
                                        }
                                        log("[MediaManager] Seek: $start-$end/$fileLength ($fileName)")
                                        return WebResourceResponse(
                                            mimeType, null, 206, "Partial Content",
                                            seekHeaders, seekStream
                                        )
                                    }

                                    // Начальная загрузка (нет Range или start=0): 200 OK без Accept-Ranges.
                                    // Accept-Ranges НЕ объявляем здесь намеренно: если он есть, Chromium
                                    // сразу шлёт Range-запрос к концу файла для moov-атома. Для non-faststart
                                    // MP4 (moov в конце) это создаёт петлю — он получает 206, но не может
                                    // вернуться к mdat через shouldInterceptRequest и повторяет запрос снова.
                                    // Без Accept-Ranges Chromium читает файл целиком — moov находится при
                                    // полном чтении. Seek после этого работает: файл уже в кеше Chromium,
                                    // он шлёт 206-запросы в seek-ветку выше и перематывает без проблем.
                                    val initHeaders = mutableMapOf<String, String>().apply {
                                        put("Content-Type", mimeType)
                                        // Accept-Ranges только для faststart: у них moov в начале,
                                        // Chromium может seekить через Range-запросы (start>0 → 206).
                                        // Non-faststart: без Accept-Ranges Chromium читает файл целиком
                                        // последовательно, находит moov в конце, видео запускается.
                                        // Seek для non-faststart работает через кеш (Cache-Control ниже).
                                        if (fastStart) put("Accept-Ranges", "bytes")
                                        put("Content-Length", fileLength.toString())
                                        put("Cache-Control", "private, max-age=3600")
                                        put("Access-Control-Allow-Origin", "*")
                                    }
                                    log("[MediaManager] Файл: $fileName ($fileLength байт) [Mime: $mimeType]")
                                    return WebResourceResponse(
                                        mimeType, null, 200, "OK",
                                        initHeaders, java.io.FileInputStream(file)
                                    )
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